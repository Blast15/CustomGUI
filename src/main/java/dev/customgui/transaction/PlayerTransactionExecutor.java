package dev.customgui.transaction;

import dev.customgui.integration.economy.EconomyBridge;
import dev.customgui.integration.enchant.CrazyEnchantmentsBridge;
import dev.customgui.integration.enchant.EnchantmentSpec;
import dev.customgui.integration.item.ItemProviderRegistry;
import dev.customgui.integration.placeholder.PlaceholderBridge;
import dev.customgui.recipe.ItemSpec;
import dev.customgui.recipe.Recipe;
import dev.customgui.requirement.PlaceholderComparison;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PlayerTransactionExecutor {
    private final ItemProviderRegistry providers;
    private final EconomyBridge economy;
    private final PlaceholderBridge placeholders;
    private final CrazyEnchantmentsBridge crazyEnchantments;
    private final IntSupplier maxBatchSize;
    private final Consumer<String> severe;
    private final HashSet<UUID> busy = new HashSet<>();
    private boolean accepting = true;

    public PlayerTransactionExecutor(ItemProviderRegistry providers, EconomyBridge economy, PlaceholderBridge placeholders,
                                     IntSupplier maxBatchSize) {
        this(providers, economy, placeholders, null, maxBatchSize, message -> Bukkit.getLogger().severe(message));
    }

    public PlayerTransactionExecutor(ItemProviderRegistry providers, EconomyBridge economy, PlaceholderBridge placeholders,
                                     IntSupplier maxBatchSize, Consumer<String> severe) {
        this(providers, economy, placeholders, null, maxBatchSize, severe);
    }

    public PlayerTransactionExecutor(ItemProviderRegistry providers, EconomyBridge economy, PlaceholderBridge placeholders,
                                     CrazyEnchantmentsBridge crazyEnchantments, IntSupplier maxBatchSize, Consumer<String> severe) {
        this.providers = providers; this.economy = economy; this.placeholders = placeholders;
        this.crazyEnchantments = crazyEnchantments; this.maxBatchSize = maxBatchSize; this.severe = severe;
    }

    public TransactionResult execute(Player player, Recipe recipe) { return execute(player, recipe, 1); }

    /** requestedBatch == 0 means the largest atomic batch allowed by inventory, money, outputs and the configured cap. */
    public TransactionResult execute(Player player, Recipe recipe, int requestedBatch) {
        UUID id = UUID.randomUUID();
        if (!Bukkit.isPrimaryThread()) return result(id, TransactionResult.Status.FAILED, "transaction-wrong-thread", 0);
        if (!accepting) return result(id, TransactionResult.Status.FAILED, "plugin-disabled", 0);
        if (requestedBatch < 0 || requestedBatch > maxBatchSize.getAsInt()) return result(id, TransactionResult.Status.REJECTED, "invalid-amount", 0);
        if (!busy.add(player.getUniqueId())) return result(id, TransactionResult.Status.BUSY, "transaction-busy", 0);
        try {
            for (var requirement : recipe.requirements()) if (!isItemOrMoney(requirement.type())) {
                String failed = checkNonItem(player, requirement);
                if (failed != null) return result(id, TransactionResult.Status.REJECTED, failed, 0);
            }
            ItemStack[] before = InventorySimulation.cloneContents(player.getInventory().getStorageContents());
            Plan plan;
            int batch;
            if (requestedBatch == 0) {
                String[] lastFailure = {"nothing-to-exchange"};
                int upperBound = batchUpperBound(player, recipe, before, maxBatchSize.getAsInt());
                batch = BatchSearch.highestFeasible(upperBound, candidate -> {
                    var attempt = plan(player, recipe, before, candidate);
                    if (attempt.plan() == null) lastFailure[0] = attempt.failure();
                    return attempt.plan() != null;
                });
                if (batch == 0) return result(id, TransactionResult.Status.REJECTED, lastFailure[0], 0);
                plan = plan(player, recipe, before, batch).plan();
            } else {
                batch = requestedBatch;
                var attempt = plan(player, recipe, before, batch);
                if (attempt.plan() == null) return result(id, TransactionResult.Status.REJECTED, attempt.failure(), 0);
                plan = attempt.plan();
            }

            var inventory = player.getInventory();
            if (!java.util.Arrays.equals(before, inventory.getStorageContents()))
                return result(id, TransactionResult.Status.REJECTED, "inventory-changed", 0);
            var withdrawal = plan.money() == 0 ? EconomyBridge.Outcome.REJECTED : economy.withdraw(player, plan.money());
            if (plan.money() > 0 && withdrawal == EconomyBridge.Outcome.REJECTED)
                return result(id, TransactionResult.Status.REJECTED, "missing-money", 0);
            if (withdrawal == EconomyBridge.Outcome.UNKNOWN) {
                severe.accept("Transaction " + id + " has ambiguous economy withdrawal; manual reconciliation may be required");
                return result(id, TransactionResult.Status.FAILED, "economy-ambiguous", 0);
            }
            boolean withdrew = withdrawal == EconomyBridge.Outcome.SUCCEEDED;
            var mutations = changedSlots(before, plan.simulated());
            var committed = new ArrayList<SlotMutation>();
            try {
                if (!java.util.Arrays.equals(before, inventory.getStorageContents())) {
                    CompensationReport report = compensate(inventory, committed, withdrew, player, plan.money());
                    if (!report.complete()) logCompensationFailure(id, report);
                    return new TransactionResult(id, report.complete() ? TransactionResult.Status.ROLLED_BACK : TransactionResult.Status.FAILED,
                        report.complete() ? "inventory-changed" : "compensation-failed", 0, report);
                }
                for (var mutation : mutations) {
                    if (!java.util.Objects.equals(mutation.before(), inventory.getItem(mutation.slot())))
                        throw new IllegalStateException("inventory changed during commit");
                    committed.add(mutation);
                    inventory.setItem(mutation.slot(), clone(mutation.after()));
                }
            } catch (RuntimeException | LinkageError ex) {
                CompensationReport report = compensate(inventory, committed, withdrew, player, plan.money());
                if (!report.complete()) logCompensationFailure(id, report);
                return new TransactionResult(id, report.complete() ? TransactionResult.Status.ROLLED_BACK : TransactionResult.Status.FAILED,
                    report.complete() ? "transaction-rolled-back" : "compensation-failed", 0, report);
            }
            return result(id, TransactionResult.Status.SUCCESS, "success", batch);
        } catch (ArithmeticException | IllegalArgumentException ex) {
            return result(id, TransactionResult.Status.REJECTED, "invalid-amount", 0);
        } catch (RuntimeException ex) {
            severe.accept("Transaction " + id + " failed before commit: " + ex);
            return result(id, TransactionResult.Status.FAILED, "transaction-failed", 0);
        } finally { busy.remove(player.getUniqueId()); }
    }

    public void release(UUID playerId) { busy.remove(playerId); }
    public void shutdown() { accepting = false; busy.clear(); }

    private CompensationReport compensate(org.bukkit.inventory.PlayerInventory inventory, java.util.List<SlotMutation> committed,
                                          boolean refundEconomy, Player player, double money) {
        return CompensationRunner.run(!committed.isEmpty(), () -> {
            for (int index = committed.size() - 1; index >= 0; index--) {
                var mutation = committed.get(index);
                ItemStack current = inventory.getItem(mutation.slot());
                if (java.util.Objects.equals(mutation.before(), current)) continue;
                if (!java.util.Objects.equals(mutation.after(), current))
                    throw new IllegalStateException("inventory slot " + mutation.slot() + " changed before rollback");
                inventory.setItem(mutation.slot(), clone(mutation.before()));
            }
        }, refundEconomy, () -> {
            var outcome = economy.deposit(player, money);
            if (outcome != EconomyBridge.Outcome.SUCCEEDED)
                throw new IllegalStateException("economy refund " + outcome.name().toLowerCase(java.util.Locale.ROOT));
        });
    }

    private void logCompensationFailure(UUID id, CompensationReport report) {
        severe.accept("Transaction " + id + " compensation incomplete (inventory=" + report.inventoryRestored()
            + ", economy=" + report.economyRestored() + "); manual reconciliation is required");
    }

    private static java.util.List<SlotMutation> changedSlots(ItemStack[] before, ItemStack[] after) {
        var mutations = new ArrayList<SlotMutation>();
        for (int slot = 0; slot < before.length; slot++)
            if (!java.util.Objects.equals(before[slot], after[slot]))
                mutations.add(new SlotMutation(slot, clone(before[slot]), clone(after[slot])));
        return mutations;
    }

    private static ItemStack clone(ItemStack stack) { return stack == null ? null : stack.clone(); }

    private int batchUpperBound(Player player, Recipe recipe, ItemStack[] before, int configuredMaximum) {
        int upper = configuredMaximum;
        double moneyPerBatch = 0;
        for (var requirement : recipe.requirements()) {
            if (requirement.type().equalsIgnoreCase("item")
                && Boolean.parseBoolean(String.valueOf(requirement.values().getOrDefault("consume", true)))) {
                ItemSpec spec = ItemSpec.from(requirement.values());
                var provider = providers.find(spec.provider()).orElse(null);
                if (provider == null) continue;
                int found = 0;
                for (ItemStack stack : before) if (matches(stack, spec, requirement.values())) found = Math.addExact(found, stack.getAmount());
                upper = Math.min(upper, found / spec.amount());
            } else if (requirement.type().equalsIgnoreCase("money") || requirement.type().equalsIgnoreCase("currency"))
                moneyPerBatch += decimal(requirement.values().get("amount"), "amount");
        }
        if (moneyPerBatch > 0 && economy != null) {
            int low = 0, high = upper;
            while (low < high) {
                int middle = low + (high - low + 1) / 2;
                if (economy.has(player, moneyPerBatch * middle)) low = middle; else high = middle - 1;
            }
            upper = low;
        }
        return upper;
    }

    private PlanAttempt plan(Player player, Recipe recipe, ItemStack[] before, int batch) {
        int[] available = new int[before.length];
        for (int slot = 0; slot < before.length; slot++) available[slot] = before[slot] == null ? 0 : before[slot].getAmount();
        var removals = new ArrayList<PlannedRemoval>();
        double money = 0;
        for (boolean consumePass : new boolean[] {true, false}) for (var requirement : recipe.requirements()) {
            if (requirement.type().equalsIgnoreCase("item")) {
                ItemSpec spec = ItemSpec.from(requirement.values());
                var provider = providers.find(spec.provider()).orElse(null);
                if (provider == null) return failed("provider-unavailable");
                boolean consume = Boolean.parseBoolean(String.valueOf(requirement.values().getOrDefault("consume", true)));
                if (consume != consumePass) continue;
                int needed = consume ? Math.multiplyExact(spec.amount(), batch) : spec.amount();
                var itemPlan = InventoryPlanner.plan(available, needed, slot -> matches(before[slot], spec, requirement.values()));
                if (itemPlan.isEmpty()) return failed("missing-items");
                if (consume) for (var removal : itemPlan) {
                    available[removal.slot()] -= removal.amount();
                    removals.add(new PlannedRemoval(removal.slot(), removal.amount(), spec));
                }
            } else if (consumePass && (requirement.type().equalsIgnoreCase("money") || requirement.type().equalsIgnoreCase("currency"))) {
                money += decimal(requirement.values().get("amount"), "amount") * batch;
            }
        }
        if (!Double.isFinite(money)) return failed("invalid-money");
        if (money > 0 && (economy == null || !economy.ready() || !economy.has(player, money))) return failed("missing-money");
        var totals = new HashMap<Integer, Integer>();
        for (var removal : removals) totals.merge(removal.slot(), removal.amount(), Math::addExact);
        var outputs = new ArrayList<ItemStack>();
        for (var output : recipe.results()) {
            if (!output.type().equalsIgnoreCase("give-item")) return failed("unsupported-result");
            ItemSpec base = ItemSpec.from(output.values());
            var provider = providers.find(base.provider()).orElse(null);
            if (provider == null) return failed("provider-unavailable");
            int amount = Math.multiplyExact(base.amount(), batch);
            ItemSpec requested = new ItemSpec(base.provider(), base.id(), base.itemType(), amount);
            ItemStack created = provider.create(requested);
            if (created == null || created.getType().isAir() || created.getAmount() != amount || !providers.matches(created, requested))
                return failed("provider-invalid-output");
            if (crazyEnchantments == null && !EnchantmentSpec.from(output.values()).isEmpty()) return failed("provider-unavailable");
            if (crazyEnchantments != null) created = crazyEnchantments.apply(created, output.values());
            outputs.add(created.clone());
        }
        var simulated = InventorySimulation.apply(before, totals, outputs);
        double totalMoney = money;
        return simulated.<PlanAttempt>map(items -> new PlanAttempt(new Plan(java.util.List.copyOf(removals), items, totalMoney), null))
            .orElseGet(() -> failed("inventory-full"));
    }

    private String checkNonItem(Player player, dev.customgui.recipe.RequirementSpec requirement) {
        var values = requirement.values();
        return switch (requirement.type().toLowerCase(java.util.Locale.ROOT)) {
            case "permission" -> player.hasPermission(required(values, "permission")) ? null : "no-permission";
            case "level" -> player.getLevel() >= integer(values.getOrDefault("amount", values.getOrDefault("min-level", 0)), "level") ? null : "missing-level";
            case "experience" -> player.getTotalExperience() >= integer(values.get("amount"), "amount") ? null : "missing-experience";
            case "world" -> player.getWorld().getName().equalsIgnoreCase(required(values, "world")) ? null : "wrong-world";
            case "game-mode" -> player.getGameMode().name().equalsIgnoreCase(required(values, "game-mode")) ? null : "wrong-game-mode";
            case "chance" -> java.util.concurrent.ThreadLocalRandom.current().nextDouble() < decimal(values.get("chance"), "chance") ? null : "chance-failed";
            case "placeholder" -> {
                if (placeholders == null) yield "provider-unavailable";
                String actual = placeholders.parse(player, required(values, "placeholder"));
                yield PlaceholderComparison.test(actual, required(values, "value"), required(values, "operator"),
                    String.valueOf(values.getOrDefault("value-type", "string"))) ? null : "placeholder-failed";
            }
            default -> "unsupported-requirement";
        };
    }

    private static boolean isItemOrMoney(String type) {
        return type.equalsIgnoreCase("item") || type.equalsIgnoreCase("money") || type.equalsIgnoreCase("currency");
    }
    private boolean matches(ItemStack stack, ItemSpec spec, Map<String, Object> values) {
        Map<String, Integer> enchantments = EnchantmentSpec.from(values);
        boolean allowEnchantedLore = !enchantments.isEmpty();
        if (!providers.matches(stack, spec, allowEnchantedLore)) return false;
        return enchantments.isEmpty() || crazyEnchantments != null && crazyEnchantments.matches(stack, values);
    }
    private static String required(Map<String, Object> values, String key) {
        Object value = values.get(key); if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required"); return String.valueOf(value);
    }
    private static int integer(Object value, String key) {
        return ItemSpec.exactInteger(value, key);
    }
    private static double decimal(Object value, String key) {
        double amount;
        if (value instanceof Number number) amount = number.doubleValue();
        else try { amount = Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be decimal"); }
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException(key + " must be finite and positive");
        return amount;
    }
    private static PlanAttempt failed(String key) { return new PlanAttempt(null, key); }
    private static TransactionResult result(UUID id, TransactionResult.Status status, String key, int batch) {
        return new TransactionResult(id, status, key, batch);
    }
    private record PlannedRemoval(int slot, int amount, ItemSpec spec) {}
    private record SlotMutation(int slot, ItemStack before, ItemStack after) {}
    private record Plan(java.util.List<PlannedRemoval> removals, ItemStack[] simulated, double money) {}
    private record PlanAttempt(Plan plan, String failure) {}
}
