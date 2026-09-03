package dev.customgui.gui;

import dev.customgui.config.ConfigSnapshot;
import dev.customgui.config.MenuItemDefinition;
import dev.customgui.config.MessageService;
import dev.customgui.integration.item.ItemProviderRegistry;
import dev.customgui.integration.placeholder.PlaceholderBridge;
import dev.customgui.recipe.ItemSpec;
import dev.customgui.recipe.Recipe;
import dev.customgui.transaction.PlayerTransactionExecutor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiService {
    private final Supplier<ConfigSnapshot> snapshot;
    private final SessionRegistry sessions;
    private final ItemProviderRegistry providers;
    private final PlayerTransactionExecutor transactions;
    private final PlaceholderBridge placeholders;
    private final MessageService messages;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private long recipeCacheRevision = Long.MIN_VALUE;
    private final Map<String, List<Recipe>> recipeCache = new HashMap<>();

    public GuiService(Supplier<ConfigSnapshot> snapshot, SessionRegistry sessions, ItemProviderRegistry providers,
                      PlayerTransactionExecutor transactions, PlaceholderBridge placeholders, MessageService messages) {
        this.snapshot = snapshot; this.sessions = sessions; this.providers = providers; this.transactions = transactions;
        this.placeholders = placeholders; this.messages = messages;
    }

    /** Returns false only when the menu ID does not exist. Denials are messaged here. */
    public boolean open(Player player, String menuId, int requestedPage) {
        var current = snapshot.get();
        var menu = current.menus().get(menuId.toLowerCase(Locale.ROOT));
        if (menu == null) return false;
        if (!canOpen(player, menu.id())) {
            player.sendMessage(messages.render("no-permission"));
            return true;
        }
        var recipes = recipes(current, menu.id());
        int pages = menu.contentSlots().isEmpty() ? 1 : Math.max(1, (recipes.size() + menu.contentSlots().size() - 1) / menu.contentSlots().size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        var session = new GuiSession(player.getUniqueId(), java.util.UUID.randomUUID(), menu.id(), Integer.toString(page),
            null, 1, current.revision(), java.time.Instant.now());
        var inventory = Bukkit.createInventory(new SessionHolder(session), menu.rows() * 9, component(player, menu.title(), Map.of()));
        var visible = menu.contentSlots().isEmpty() ? List.<Recipe>of() : Paginator.page(recipes, page, menu.contentSlots().size());
        for (int index = 0; index < visible.size(); index++) inventory.setItem(menu.contentSlots().get(index), recipeIcon(player, menu.id(), visible.get(index)));
        for (var entry : staticItems(player, menu.id()).entrySet()) inventory.setItem(entry.getKey(), itemIcon(player, entry.getValue()));
        sessions.put(session);
        player.openInventory(inventory);
        return true;
    }

    public void click(Player player, SessionHolder holder, int rawSlot, ClickType clickType) {
        var session = sessions.valid(player.getUniqueId(), holder.session().sessionId()).orElse(null);
        var current = snapshot.get();
        if (session == null || session.configRevision() != current.revision()) { player.closeInventory(); return; }
        var menu = current.menus().get(session.menuId());
        if (menu == null) { player.closeInventory(); return; }
        int page = Integer.parseInt(session.currentPage());
        String click = clickName(clickType);
        if (click == null) return;
        var item = staticItems(player, menu.id()).get(rawSlot);
        if (item != null) {
            String permission = item.clickPermissions().getOrDefault(click, item.clickPermissions().getOrDefault("click", ""));
            if (!permission.isBlank() && !player.hasPermission(permission)) { player.sendMessage(messages.render("no-permission")); return; }
            executeActions(player, menu.id(), page, item.actions().getOrDefault(click, item.actions().getOrDefault("click", List.of())));
            return;
        }
        int index = menu.contentSlots().indexOf(rawSlot);
        if (index < 0) return;
        var recipes = recipes(current, menu.id());
        int recipeIndex = page * menu.contentSlots().size() + index;
        if (recipeIndex >= recipes.size()) return;
        String mode = menu.recipeActions().getOrDefault(click, menu.recipeActions().get("click"));
        if (mode != null) exchange(player, menu.id(), page, recipes.get(recipeIndex), mode);
    }

    public boolean allowPlayerInventoryInteraction() { return snapshot.get().allowPlayerInventoryInteraction(); }

    public boolean canOpen(org.bukkit.command.CommandSender sender, String menuId) {
        var menu = snapshot.get().menus().get(menuId.toLowerCase(Locale.ROOT));
        return menu != null && (menu.permission().isBlank() || sender.hasPermission(menu.permission())
            || sender.hasPermission("customgui.bypass.*") || sender.hasPermission("customgui.bypass." + menu.id()));
    }

    private void executeActions(Player player, String currentMenu, int page, List<String> configured) {
        for (String raw : configured) {
            var action = MenuAction.parse(raw);
            switch (action.type()) {
                case CLOSE -> { player.closeInventory(); return; }
                case REFRESH -> { open(player, currentMenu, page); return; }
                case NEXT_PAGE -> { open(player, currentMenu, page + 1); return; }
                case PREVIOUS_PAGE -> { open(player, currentMenu, page - 1); return; }
                case OPEN_MENU -> {
                    String[] args = action.value().split("\\s+", 2);
                    int targetPage = 0;
                    if (args.length == 2) try { targetPage = Math.max(0, Integer.parseInt(args[1]) - 1); }
                    catch (NumberFormatException ex) { player.sendMessage(messages.render("invalid-page")); return; }
                    if (!open(player, args[0], targetPage)) player.sendMessage(messages.render("unknown-menu", Map.of("menu", args[0])));
                    return;
                }
                case RECIPE -> {
                    String[] args = action.value().split("\\s+", 2);
                    var recipe = snapshot.get().recipes().find(args[0]).orElse(null);
                    if (recipe == null) player.sendMessage(messages.render("unknown-recipe", Map.of("recipe", args[0])));
                    else exchange(player, currentMenu, page, recipe, args.length == 1 ? "1" : args[1]);
                    return;
                }
                case MESSAGE -> player.sendMessage(component(player, action.value(), Map.of()));
                case PLAYER_COMMAND -> dispatch(player, player, action.value());
                case CONSOLE_COMMAND -> dispatch(Bukkit.getConsoleSender(), player, action.value());
            }
        }
    }

    private void exchange(Player player, String menu, int page, Recipe recipe, String mode) {
        int amount;
        try { amount = mode.equalsIgnoreCase("all") ? 0 : Integer.parseInt(mode); }
        catch (NumberFormatException ex) { player.sendMessage(messages.render("invalid-amount")); return; }
        var result = transactions.execute(player, recipe, amount);
        player.sendMessage(messages.render(result.messageKey(), Map.of("transaction_id", result.transactionId().toString(),
            "amount", Integer.toString(result.batchSize()))));
        if (result.status() == dev.customgui.transaction.TransactionResult.Status.SUCCESS) open(player, menu, page);
    }

    private List<Recipe> recipes(ConfigSnapshot current, String menuId) {
        if (recipeCacheRevision != current.revision()) { recipeCache.clear(); recipeCacheRevision = current.revision(); }
        var menu = current.menus().get(menuId);
        return recipeCache.computeIfAbsent(menuId, ignored -> current.recipes().all().stream().filter(Recipe::enabled)
            .filter(recipe -> menu.recipeGroups().isEmpty() || menu.recipeGroups().contains(recipe.group().toLowerCase(Locale.ROOT)))
            .filter(recipe -> menu.recipeCategories().isEmpty() || menu.recipeCategories().contains(recipe.category().toLowerCase(Locale.ROOT)))
            .sorted(Comparator.comparing(Recipe::id)).toList());
    }

    private Map<Integer, MenuItemDefinition> staticItems(Player player, String menuId) {
        var menu = snapshot.get().menus().get(menuId);
        var resolved = new HashMap<Integer, MenuItemDefinition>();
        for (var item : menu.items()) if (item.viewPermission().isBlank() || player.hasPermission(item.viewPermission()))
            for (int slot : item.slots()) resolved.put(slot, item);
        return resolved;
    }

    private ItemStack recipeIcon(Player player, String menuId, Recipe recipe) {
        ItemStack stack = null;
        for (var result : recipe.results()) if (result.type().equalsIgnoreCase("give-item")) try {
            var spec = ItemSpec.from(result.values());
            var provider = providers.find(spec.provider()).orElse(null);
            if (provider != null) { stack = provider.create(new ItemSpec(spec.provider(), spec.id(), spec.itemType(), 1)); break; }
        } catch (RuntimeException | LinkageError ignored) { /* unavailable previews fall back to paper */ }
        if (stack == null) stack = new ItemStack(Material.PAPER);
        var menu = snapshot.get().menus().get(menuId);
        var values = Map.of("recipe_id", recipe.id(), "recipe_group", recipe.group(), "recipe_category", recipe.category());
        return decorate(player, stack, menu.recipeName(), menu.recipeLore(), values);
    }

    private ItemStack itemIcon(Player player, MenuItemDefinition item) {
        var provider = providers.find(item.icon().provider()).orElse(null);
        ItemStack stack;
        try { stack = provider == null ? new ItemStack(Material.BARRIER) : provider.create(item.icon()); }
        catch (RuntimeException ex) { stack = new ItemStack(Material.BARRIER); }
        stack = decorate(player, stack, item.name(), item.lore(), Map.of("item_id", item.id()));
        applyVisualOptions(stack, item);
        return stack;
    }

    @SuppressWarnings("deprecation")
    private static void applyVisualOptions(ItemStack stack, MenuItemDefinition item) {
        ItemMeta meta = stack.getItemMeta();
        if (item.glow()) meta.setEnchantmentGlintOverride(true);
        if (item.customModelData() != null) meta.setCustomModelData(item.customModelData());
        if (item.hideTooltip()) meta.setHideTooltip(true);
        for (String flag : item.itemFlags()) try { meta.addItemFlags(org.bukkit.inventory.ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ignored) { /* invalid visual flags do not weaken click identity */ }
        stack.setItemMeta(meta);
    }

    private ItemStack decorate(Player player, ItemStack stack, String name, List<String> lore, Map<String, String> values) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(component(player, replace(name, values), Map.of()));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(line -> component(player, replace(line, values), Map.of())).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private Component component(Player player, String input, Map<String, String> values) {
        String text = replace(input, values);
        if (placeholders != null) text = safePlaceholders(player, text);
        try { return mini.deserialize(text); } catch (RuntimeException ex) { return Component.text(text); }
    }

    private static String replace(String input, Map<String, String> values) {
        String output = input;
        for (var entry : values.entrySet()) output = output.replace('%' + entry.getKey() + '%', entry.getValue());
        return output;
    }

    private void dispatch(org.bukkit.command.CommandSender sender, Player player, String configured) {
        String command = command(player, configured);
        if (command != null) Bukkit.dispatchCommand(sender, command);
    }

    private String command(Player player, String configured) {
        String value = configured.replace("%player%", player.getName());
        if (placeholders != null) try { value = placeholders.parse(player, value); } catch (RuntimeException ignored) { /* execute raw */ }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            player.sendMessage(messages.render("transaction-failed"));
            return null;
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private String safePlaceholders(Player player, String configured) {
        var matcher = java.util.regex.Pattern.compile("%[^%\\r\\n]{1,128}%").matcher(configured);
        var output = new StringBuffer();
        while (matcher.find()) {
            String replacement;
            try { replacement = mini.escapeTags(placeholders.parse(player, matcher.group())); }
            catch (RuntimeException ignored) { replacement = matcher.group(); }
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(output).toString();
    }

    private static String clickName(ClickType click) {
        return switch (click) {
            case SHIFT_LEFT -> "shift-left";
            case SHIFT_RIGHT -> "shift-right";
            case LEFT -> "left";
            case RIGHT -> "right";
            case MIDDLE -> "middle";
            default -> null;
        };
    }
}
