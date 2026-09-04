package dev.customgui.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import dev.customgui.integration.item.ItemProvider;
import dev.customgui.integration.item.ItemProviderRegistry;
import dev.customgui.integration.item.VanillaItemProvider;
import dev.customgui.integration.economy.EconomyBridge;
import dev.customgui.recipe.ItemSpec;
import dev.customgui.recipe.Recipe;
import dev.customgui.recipe.RequirementSpec;
import dev.customgui.recipe.ResultSpec;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class CraftingTransactionRegressionTest {
    private Server server;
    private ItemFactory itemFactory;
    private ItemProviderRegistry providers;
    private PlayerTransactionExecutor executor;
    private CustomItemProvider customProvider;
    private Player player;
    private PlayerInventory inventory;
    private ItemStack[] storageContents;

    private static final NamespacedKey CUSTOM_HELMET_KEY = new NamespacedKey("customgui", "custom_diamond_helmet");

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        itemFactory = mock(ItemFactory.class);
        when(server.getItemFactory()).thenReturn(itemFactory);
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getLogger()).thenReturn(java.util.logging.Logger.getLogger("Minecraft"));
        setBukkitServer(server);

        when(itemFactory.getItemMeta(any())).thenAnswer(inv -> createFreshMeta((Material) inv.getArgument(0)));
        when(itemFactory.equals(any(), any())).thenAnswer(inv -> {
            ItemMeta a = inv.getArgument(0), b = inv.getArgument(1);
            return compareMeta(a, b);
        });
        when(itemFactory.asMetaFor(any(ItemMeta.class), any(ItemStack.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemFactory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenAnswer(inv -> inv.getArgument(0));

        providers = new ItemProviderRegistry();
        providers.register(new VanillaItemProvider());
        customProvider = new CustomItemProvider();
        providers.register(customProvider);

        executor = new PlayerTransactionExecutor(providers, null, null, () -> 64, System.err::println);

        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);

        storageContents = new ItemStack[36];
        when(inventory.getStorageContents()).thenAnswer(inv -> Arrays.copyOf(storageContents, storageContents.length));
        when(inventory.getItem(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> storageContents[(int) inv.getArgument(0)]);
        org.mockito.Mockito.doAnswer(inv -> {
            storageContents[(int) inv.getArgument(0)] = inv.getArgument(1);
            return null;
        }).when(inventory).setItem(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(ItemStack.class));
    }

    @AfterEach
    void tearDown() {
        setBukkitServer(null);
    }

    private Recipe createUpgradeHelmetRecipe() {
        return new Recipe("upgrade_helmet", "forge", "armor", true,
            List.of(
                new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIAMOND_HELMET", "amount", 1, "consume", true)),
                new RequirementSpec("item", Map.of("provider", "vanilla", "material", "EMERALD", "amount", 1, "consume", true)),
                new RequirementSpec("item", Map.of("provider", "vanilla", "material", "NETHERITE_INGOT", "amount", 1, "consume", true))
            ),
            List.of(
                new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_diamond_helmet", "amount", 1))
            )
        );
    }

    private ItemStack vanillaHelmet() {
        return createStack(Material.DIAMOND_HELMET, 1, false, null);
    }

    private ItemStack customHelmet() {
        return createStack(Material.DIAMOND_HELMET, 1, true, "Custom Diamond Helmet");
    }

    private ItemStack ingredientA() {
        return createStack(Material.EMERALD, 1, false, null);
    }

    private ItemStack ingredientB() {
        return createStack(Material.NETHERITE_INGOT, 1, false, null);
    }

    @Test
    void craftFirstTimeSuccess() {
        storageContents[0] = vanillaHelmet();
        storageContents[1] = ingredientA();
        storageContents[2] = ingredientB();

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.SUCCESS, result.status());
        assertNull(storageContents[1]);
        assertNull(storageContents[2]);
        assertNotNull(storageContents[0]);
        assertTrue(customProvider.matches(storageContents[0], new ItemSpec("custom", "custom_diamond_helmet", "", 1)));
    }

    /** Regression Test 2: Main bug test */
    @Test
    void craftSameCustomHelmetTwiceWhilePreviousOutputRemains() {
        // 1st craft
        storageContents[0] = vanillaHelmet();
        storageContents[1] = ingredientA();
        storageContents[2] = ingredientB();

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult firstResult = executor.execute(player, recipe, 1);
        assertEquals(TransactionResult.Status.SUCCESS, firstResult.status());

        // Player now has custom helmet at slot 0. Add new ingredients at slots 5, 10, 11
        storageContents[5] = vanillaHelmet();
        storageContents[10] = ingredientA();
        storageContents[11] = ingredientB();

        TransactionResult secondResult = executor.execute(player, recipe, 1);
        assertEquals(TransactionResult.Status.SUCCESS, secondResult.status());

        // Slot 5 (vanilla helmet) must be consumed
        assertNull(storageContents[5]);
        // Slot 10 and 11 must be consumed
        assertNull(storageContents[10]);
        assertNull(storageContents[11]);

        // There must be 2 custom helmets total in distinct slots
        int customCount = 0;
        int customSlots = 0;
        for (ItemStack stack : storageContents) {
            if (stack != null && customProvider.matches(stack, new ItemSpec("custom", "custom_diamond_helmet", "", 1))) {
                customCount += stack.getAmount();
                customSlots++;
            }
        }
        assertEquals(2, customCount);
        assertEquals(2, customSlots);
        assertNotNull(storageContents[0]); // Original helmet preserved at slot 0
    }

    /** Regression Test 3: Custom helmet must not satisfy vanilla helmet requirement */
    @Test
    void customHelmetMustNotSatisfyVanillaHelmetRequirement() {
        storageContents[0] = customHelmet();
        storageContents[1] = ingredientA();
        storageContents[2] = ingredientB();

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.REJECTED, result.status());
        assertEquals("missing-items", result.messageKey());

        // Absolutely nothing should be consumed
        assertNotNull(storageContents[0]);
        assertTrue(customProvider.matches(storageContents[0], new ItemSpec("custom", "custom_diamond_helmet", "", 1)));
        assertNotNull(storageContents[1]);
        assertEquals(Material.EMERALD, storageContents[1].getType());
        assertNotNull(storageContents[2]);
        assertEquals(Material.NETHERITE_INGOT, storageContents[2].getType());
    }

    /** Regression Test 4: Planner prefers valid vanilla helmet over custom helmet */
    @Test
    void plannerPrefersValidVanillaHelmetOverCustomHelmet() {
        storageContents[0] = customHelmet();
        storageContents[1] = vanillaHelmet();
        storageContents[2] = ingredientA();
        storageContents[3] = ingredientB();

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.SUCCESS, result.status());

        // Slot 0 (custom helmet) must NOT be consumed
        assertNotNull(storageContents[0]);
        assertTrue(customProvider.matches(storageContents[0], new ItemSpec("custom", "custom_diamond_helmet", "", 1)));

        // Slot 1 (vanilla helmet) must be consumed and can receive the new helmet
        assertNotNull(storageContents[1]);
        assertTrue(customProvider.matches(storageContents[1], new ItemSpec("custom", "custom_diamond_helmet", "", 1)));

        assertNull(storageContents[2]);
        assertNull(storageContents[3]);
    }

    /** Regression Test 5: Repeated crafts do not lose items */
    @Test
    void repeatedNonStackableCustomOutputDoesNotLoseItems() {
        Recipe recipe = createUpgradeHelmetRecipe();

        for (int i = 0; i < 3; i++) {
            // Find empty slots for ingredients
            int slotH = -1, slotA = -1, slotB = -1;
            for (int s = 0; s < storageContents.length; s++) {
                if (storageContents[s] == null) {
                    if (slotH == -1) slotH = s;
                    else if (slotA == -1) slotA = s;
                    else if (slotB == -1) { slotB = s; break; }
                }
            }
            storageContents[slotH] = vanillaHelmet();
            storageContents[slotA] = ingredientA();
            storageContents[slotB] = ingredientB();

            TransactionResult result = executor.execute(player, recipe, 1);
            assertEquals(TransactionResult.Status.SUCCESS, result.status());
        }

        int customCount = 0;
        for (ItemStack stack : storageContents) {
            if (stack != null && customProvider.matches(stack, new ItemSpec("custom", "custom_diamond_helmet", "", 1))) {
                customCount += stack.getAmount();
            }
        }
        assertEquals(3, customCount);
    }

    /** Regression Test 6: Missing helmet on second craft fails atomically */
    @Test
    void secondCraftWithoutNewVanillaHelmetMustFailAtomically() {
        // 1st craft succeeds
        storageContents[0] = vanillaHelmet();
        storageContents[1] = ingredientA();
        storageContents[2] = ingredientB();

        Recipe recipe = createUpgradeHelmetRecipe();
        assertEquals(TransactionResult.Status.SUCCESS, executor.execute(player, recipe, 1).status());

        // Add ingredients but NO vanilla helmet
        storageContents[10] = ingredientA();
        storageContents[11] = ingredientB();

        ItemStack[] snapshot = InventorySimulation.cloneContents(storageContents);

        TransactionResult result = executor.execute(player, recipe, 1);
        assertEquals(TransactionResult.Status.REJECTED, result.status());

        // Verify byte/logic equality
        assertEquals(snapshot.length, storageContents.length);
        for (int i = 0; i < snapshot.length; i++) {
            if (snapshot[i] == null) assertNull(storageContents[i]);
            else {
                assertNotNull(storageContents[i]);
                assertEquals(snapshot[i].getType(), storageContents[i].getType());
                assertEquals(snapshot[i].getAmount(), storageContents[i].getAmount());
            }
        }
    }

    /** Regression Test 7: Existing non-stackable output uses another empty slot */
    @Test
    void existingNonStackableOutputUsesAnotherSlot() {
        storageContents[0] = customHelmet();
        storageContents[5] = vanillaHelmet();
        storageContents[10] = ingredientA();
        storageContents[11] = ingredientB();
        storageContents[20] = null; // empty slot

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.SUCCESS, result.status());
        assertNotNull(storageContents[0]); // Old custom helmet unchanged at slot 0
        assertTrue(customProvider.matches(storageContents[0], new ItemSpec("custom", "custom_diamond_helmet", "", 1)));

        // Total custom helmets = 2
        int count = 0;
        for (ItemStack stack : storageContents) {
            if (stack != null && customProvider.matches(stack, new ItemSpec("custom", "custom_diamond_helmet", "", 1))) {
                count += stack.getAmount();
            }
        }
        assertEquals(2, count);
    }

    /** Regression Test 8: Consumed helmet frees slot for craft output */
    @Test
    void consumedHelmetFreesSlotForCraftOutput() {
        // Fill inventory completely
        for (int i = 0; i < 36; i++) {
            storageContents[i] = createStack(Material.DIRT, 64, false, null);
        }
        // Slot 5 is vanilla helmet, 6 is ingredientA, 7 is ingredientB
        storageContents[5] = vanillaHelmet();
        storageContents[6] = ingredientA();
        storageContents[7] = ingredientB();

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.SUCCESS, result.status());
        assertNotNull(storageContents[5]);
        assertTrue(customProvider.matches(storageContents[5], new ItemSpec("custom", "custom_diamond_helmet", "", 1)));
    }

    /** Regression Test 9: Insufficient output capacity does not consume ingredients */
    @Test
    void insufficientOutputCapacityDoesNotConsumeIngredients() {
        // Fill inventory completely with non-consumable items except non-consuming requirements
        for (int i = 0; i < 36; i++) {
            storageContents[i] = createStack(Material.DIRT, 64, false, null);
        }
        storageContents[0] = vanillaHelmet(); // non-consuming!
        storageContents[1] = ingredientA();

        Recipe recipe = new Recipe("no_room", "forge", "default", true,
            List.of(
                new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIAMOND_HELMET", "amount", 1, "consume", false)),
                new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIRT", "amount", 1, "consume", false))
            ),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_diamond_helmet", "amount", 1)))
        );

        TransactionResult result = executor.execute(player, recipe, 1);
        assertEquals(TransactionResult.Status.REJECTED, result.status());
        assertEquals("inventory-full", result.messageKey());

        assertEquals(Material.DIAMOND_HELMET, storageContents[0].getType());
    }

    /** Regression Test 10: Custom provider can create same non-stackable output repeatedly */
    @Test
    void customProviderCanCreateSameNonStackableOutputRepeatedly() {
        ItemSpec spec = new ItemSpec("custom", "custom_diamond_helmet", "", 1);
        ItemStack item1 = customProvider.create(spec);
        ItemStack item2 = customProvider.create(spec);

        assertNotNull(item1);
        assertNotNull(item2);
        assertEquals(Material.DIAMOND_HELMET, item1.getType());
        assertEquals(Material.DIAMOND_HELMET, item2.getType());
        assertTrue(customProvider.matches(item1, spec));
        assertTrue(customProvider.matches(item2, spec));
        assertFalse(providers.find("vanilla").orElseThrow().matches(item1, new ItemSpec("vanilla", "DIAMOND_HELMET", "", 1)));
    }

    @Test
    void retainedRequirementMustRemainAfterOverlappingConsumption() {
        storageContents[0] = createStack(Material.DIAMOND, 64, false, null);
        Recipe recipe = new Recipe("retained_key", "exchange", "default", true, List.of(
            new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIAMOND", "amount", 1, "consume", false)),
            new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIAMOND", "amount", 64, "consume", true))),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_diamond_helmet", "amount", 1))));

        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.REJECTED, result.status());
        assertEquals("missing-items", result.messageKey());
        assertEquals(64, storageContents[0].getAmount());
    }

    @Test
    void refundsSuccessfulWithdrawalWhenPostWithdrawalInventoryReadFails() {
        storageContents[0] = createStack(Material.DIAMOND, 1, false, null);
        ItemStack[] stable = InventorySimulation.cloneContents(storageContents);
        when(inventory.getStorageContents()).thenReturn(stable, stable).thenThrow(new IllegalStateException("fault injection"));
        EconomyBridge economy = mock(EconomyBridge.class);
        when(economy.ready()).thenReturn(true);
        when(economy.has(player, 10.0)).thenReturn(true);
        when(economy.withdraw(player, 10.0)).thenReturn(EconomyBridge.Outcome.SUCCEEDED);
        when(economy.deposit(player, 10.0)).thenReturn(EconomyBridge.Outcome.SUCCEEDED);
        executor = new PlayerTransactionExecutor(providers, economy, null, () -> 64, System.err::println);
        Recipe recipe = new Recipe("paid", "exchange", "default", true,
            List.of(new RequirementSpec("money", Map.of("amount", 10.0))),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_diamond_helmet", "amount", 1))));

        TransactionResult result = executor.execute(player, recipe, 1);

        verify(economy).withdraw(player, 10.0);
        verify(economy).deposit(player, 10.0);
        assertEquals(TransactionResult.Status.ROLLED_BACK, result.status(), result.toString());
    }

    @Test
    void allCanUseSlotFreedOnlyAtLargerBatch() {
        for (int slot = 0; slot < storageContents.length; slot++) storageContents[slot] = createStack(Material.DIRT, 64, false, null);
        storageContents[0] = createStack(Material.DIAMOND, 64, false, null);
        Recipe recipe = new Recipe("all_frees_slot", "exchange", "default", true,
            List.of(new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIAMOND", "amount", 1))),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_emerald", "amount", 1))));

        TransactionResult result = executor.execute(player, recipe, 0);

        assertEquals(TransactionResult.Status.SUCCESS, result.status(), result.toString());
        assertEquals(64, result.batchSize());
        assertEquals(Material.EMERALD, storageContents[0].getType());
        assertEquals(64, storageContents[0].getAmount());
    }

    @Test
    void commitDoesNotOverwriteUnrelatedInventoryChange() {
        storageContents[0] = vanillaHelmet();
        storageContents[1] = ingredientA();
        storageContents[2] = ingredientB();
        ItemStack unrelated = createStack(Material.GOLD_INGOT, 7, false, null);
        org.mockito.Mockito.doAnswer(inv -> {
            int slot = inv.getArgument(0);
            storageContents[35] = unrelated;
            storageContents[slot] = inv.getArgument(1);
            return null;
        }).when(inventory).setItem(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(ItemStack.class));

        TransactionResult result = executor.execute(player, createUpgradeHelmetRecipe(), 1);

        assertEquals(TransactionResult.Status.SUCCESS, result.status(), result.toString());
        assertEquals(unrelated, storageContents[35]);
    }

    @Test
    void partialSlotCommitRollsBackOnlyTransactionMutations() {
        storageContents[0] = vanillaHelmet();
        storageContents[1] = ingredientA();
        storageContents[2] = ingredientB();
        ItemStack[] before = InventorySimulation.cloneContents(storageContents);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(inv -> {
            int slot = inv.getArgument(0);
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("fault injection");
            storageContents[slot] = inv.getArgument(1);
            return null;
        }).when(inventory).setItem(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(ItemStack.class));

        TransactionResult result = executor.execute(player, createUpgradeHelmetRecipe(), 1);

        assertEquals(TransactionResult.Status.ROLLED_BACK, result.status(), result.toString());
        assertTrue(result.compensation().complete());
        assertTrue(Arrays.equals(before, storageContents));
    }

    @Test
    void silentInventoryWriteFailureIsDetectedAndRolledBack() {
        storageContents[0] = vanillaHelmet();
        storageContents[1] = ingredientA();
        storageContents[2] = ingredientB();
        ItemStack[] before = InventorySimulation.cloneContents(storageContents);
        org.mockito.Mockito.doNothing().when(inventory).setItem(
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(ItemStack.class));

        TransactionResult result = executor.execute(player, createUpgradeHelmetRecipe(), 1);

        assertEquals(TransactionResult.Status.ROLLED_BACK, result.status(), result.toString());
        assertTrue(result.compensation().complete());
        assertTrue(Arrays.equals(before, storageContents));
    }

    @Test
    void rejectsReentrantTransactionForSamePlayer() {
        EconomyBridge economy = mock(EconomyBridge.class);
        when(economy.ready()).thenReturn(true);
        when(economy.has(player, 1.0)).thenReturn(true);
        var nested = new java.util.concurrent.atomic.AtomicReference<TransactionResult>();
        Recipe free = new Recipe("nested", "exchange", "default", true, List.of(),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_emerald", "amount", 1))));
        executor = new PlayerTransactionExecutor(providers, economy, null, () -> 64, System.err::println);
        when(economy.withdraw(player, 1.0)).thenAnswer(invocation -> {
            nested.set(executor.execute(player, free, 1));
            return EconomyBridge.Outcome.REJECTED;
        });
        Recipe paid = new Recipe("paid", "exchange", "default", true,
            List.of(new RequirementSpec("money", Map.of("amount", 1.0))),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_emerald", "amount", 1))));

        TransactionResult outer = executor.execute(player, paid, 1);

        assertEquals(TransactionResult.Status.REJECTED, outer.status());
        assertNotNull(nested.get());
        assertEquals(TransactionResult.Status.BUSY, nested.get().status());
    }

    @Test
    void fiftySequentialTransactionsPreserveExactTotals() {
        storageContents[0] = createStack(Material.DIAMOND, 50, false, null);
        Recipe recipe = new Recipe("stress", "exchange", "default", true,
            List.of(new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIAMOND", "amount", 1))),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_emerald", "amount", 1))));

        for (int attempt = 0; attempt < 50; attempt++)
            assertEquals(TransactionResult.Status.SUCCESS, executor.execute(player, recipe, 1).status());

        int diamonds = Arrays.stream(storageContents).filter(Objects::nonNull)
            .filter(stack -> stack.getType() == Material.DIAMOND).mapToInt(ItemStack::getAmount).sum();
        int outputs = Arrays.stream(storageContents).filter(Objects::nonNull)
            .filter(stack -> customProvider.matches(stack, new ItemSpec("custom", "custom_emerald", "", 1)))
            .mapToInt(ItemStack::getAmount).sum();
        assertEquals(0, diamonds);
        assertEquals(50, outputs);
    }

    @Test
    void consumesLooseItemsThenItemsInsideShulkerAtomically() {
        storageContents[0] = createStack(Material.DIAMOND, 2, false, null);
        storageContents[1] = new TestShulkerStack(new ItemStack[] {
            createStack(Material.DIAMOND, 5, false, null),
            createStack(Material.GOLD_INGOT, 3, false, null)
        });
        Recipe recipe = new Recipe("shulker_items", "exchange", "default", true,
            List.of(new RequirementSpec("item", Map.of(
                "provider", "vanilla", "material", "DIAMOND", "amount", 4,
                "consume", true, "include-shulkers", true))),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_emerald", "amount", 1))));

        TransactionResult result = executor.execute(player, recipe, 0);

        assertEquals(TransactionResult.Status.SUCCESS, result.status(), result.toString());
        assertEquals(1, result.batchSize());
        ItemStack[] contents = ((TestShulkerStack) storageContents[1]).contents();
        assertEquals(3, contents[0].getAmount());
        assertEquals(3, contents[1].getAmount());
        assertTrue(customProvider.matches(storageContents[0], new ItemSpec("custom", "custom_emerald", "", 1)));
    }

    @Test
    void ignoresShulkerContentsUnlessRequirementOptsIn() {
        storageContents[0] = new TestShulkerStack(new ItemStack[] {createStack(Material.DIAMOND, 5, false, null)});
        ItemStack before = storageContents[0].clone();
        Recipe recipe = new Recipe("no_shulker_scan", "exchange", "default", true,
            List.of(new RequirementSpec("item", Map.of("provider", "vanilla", "material", "DIAMOND", "amount", 1))),
            List.of(new ResultSpec("give-item", Map.of("provider", "custom", "id", "custom_emerald", "amount", 1))));

        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.REJECTED, result.status());
        assertEquals("missing-items", result.messageKey());
        assertEquals(before, storageContents[0]);
    }

    /** Regression Test 11: Failed transaction never partially consumes requirements */
    @Test
    void failedTransactionNeverPartiallyConsumesRequirements() {
        storageContents[0] = vanillaHelmet();
        storageContents[1] = ingredientA();
        // Missing ingredientB

        ItemStack[] snapshot = InventorySimulation.cloneContents(storageContents);

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult result = executor.execute(player, recipe, 1);

        assertEquals(TransactionResult.Status.REJECTED, result.status());
        assertEquals("missing-items", result.messageKey());

        for (int i = 0; i < snapshot.length; i++) {
            if (snapshot[i] == null) assertNull(storageContents[i]);
            else {
                assertNotNull(storageContents[i]);
                assertEquals(snapshot[i].getType(), storageContents[i].getType());
                assertEquals(snapshot[i].getAmount(), storageContents[i].getAmount());
            }
        }
    }

    /** Batch crafting test */
    @Test
    void batchCraftingNonStackableOutputSplitsIntoMultipleSlots() {
        storageContents[0] = vanillaHelmet();
        storageContents[1] = vanillaHelmet();
        storageContents[2] = createStack(Material.EMERALD, 2, false, null);
        storageContents[3] = createStack(Material.NETHERITE_INGOT, 2, false, null);

        Recipe recipe = createUpgradeHelmetRecipe();
        TransactionResult result = executor.execute(player, recipe, 2);

        assertEquals(TransactionResult.Status.SUCCESS, result.status());
        assertEquals(2, result.batchSize());

        int count = 0;
        for (ItemStack stack : storageContents) {
            if (stack != null && customProvider.matches(stack, new ItemSpec("custom", "custom_diamond_helmet", "", 1))) {
                count += stack.getAmount();
                assertEquals(1, stack.getAmount()); // Each must be amount 1 in separate slot
            }
        }
        assertEquals(2, count);
    }

    /** Generality test across materials */
    @Test
    void customMatchingIsGenericAcrossMaterials() {
        Material[] materials = {
            Material.DIAMOND_SWORD,
            Material.DIAMOND_CHESTPLATE,
            Material.PAPER,
            Material.STICK,
            Material.IRON_INGOT
        };

        for (Material mat : materials) {
            ItemStack customStack = createStack(mat, 1, true, "Custom " + mat.name());
            ItemStack vanillaStack = createStack(mat, 1, false, null);

            ItemSpec vanillaSpec = new ItemSpec("vanilla", mat.name(), "", 1);

            assertFalse(providers.matches(customStack, vanillaSpec), "Custom item with base " + mat + " must not match vanilla requirement");
            assertTrue(providers.matches(vanillaStack, vanillaSpec), "Vanilla item with base " + mat + " must match vanilla requirement");
        }
    }

    // --- Helper mocks & classes ---

    private static ItemMeta createFreshMeta(Material material) {
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.getKeys()).thenReturn(Set.of());
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(meta.hasDisplayName()).thenReturn(false);
        when(meta.hasLore()).thenReturn(false);
        when(meta.hasCustomModelData()).thenReturn(false);
        when(meta.hasAttributeModifiers()).thenReturn(false);
        when(meta.getItemFlags()).thenReturn(Set.of());
        when(meta.clone()).thenAnswer(inv -> createFreshMeta(material));
        return meta;
    }

    private static boolean compareMeta(ItemMeta a, ItemMeta b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        boolean aCustom = a.hasCustomModelData() || !a.getPersistentDataContainer().getKeys().isEmpty();
        boolean bCustom = b.hasCustomModelData() || !b.getPersistentDataContainer().getKeys().isEmpty();
        if (aCustom != bCustom) return false;
        return Objects.equals(a.getPersistentDataContainer().getKeys(), b.getPersistentDataContainer().getKeys())
            && Objects.equals(a.hasDisplayName(), b.hasDisplayName());
    }

    private static ItemStack createStack(Material material, int amount, boolean custom, String name) {
        return new TestItemStack(material, amount, custom, name);
    }

    public static class TestItemStack extends ItemStack {
        private final Material material;
        private int amount;
        private final ItemMeta meta;
        private final boolean custom;
        private final String name;

        public TestItemStack(Material material, int amount, boolean custom, String name) {
            this.material = material;
            this.amount = amount;
            this.custom = custom;
            this.name = name;
            this.meta = mock(ItemMeta.class);
            configureMeta(this.meta, custom, name);
        }

        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return maxStackSize(material); }
        @Override public boolean hasItemMeta() { return true; }
        @Override public ItemMeta getItemMeta() { return meta; }

        @Override
        public boolean isSimilar(ItemStack other) {
            if (other == null || other.getType() != material) return false;
            ItemMeta otherMeta = other.getItemMeta();
            if (otherMeta == null) return !custom;
            return compareMeta(this.meta, otherMeta);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof ItemStack other)) return false;
            return other.getType() == material && other.getAmount() == amount && isSimilar(other);
        }

        @Override
        public TestItemStack clone() {
            return new TestItemStack(material, amount, custom, name);
        }
    }

    private static final class TestShulkerStack extends ItemStack {
        private ItemStack[] contents;
        private int amount = 1;

        private TestShulkerStack(ItemStack[] contents) {
            this.contents = InventorySimulation.cloneContents(contents);
        }

        ItemStack[] contents() { return InventorySimulation.cloneContents(contents); }
        @Override public Material getType() { return Material.WHITE_SHULKER_BOX; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return 1; }
        @Override public boolean hasItemMeta() { return true; }

        @Override
        public BlockStateMeta getItemMeta() {
            ItemStack[][] current = {InventorySimulation.cloneContents(contents)};
            Inventory boxInventory = mock(Inventory.class);
            when(boxInventory.getStorageContents()).thenAnswer(inv -> InventorySimulation.cloneContents(current[0]));
            org.mockito.Mockito.doAnswer(inv -> {
                current[0] = InventorySimulation.cloneContents(inv.getArgument(0));
                return null;
            }).when(boxInventory).setStorageContents(any(ItemStack[].class));
            ShulkerBox state = mock(ShulkerBox.class);
            when(state.getInventory()).thenReturn(boxInventory);
            BlockStateMeta meta = mock(BlockStateMeta.class);
            configureMeta(meta, false, null);
            when(meta.getBlockState()).thenReturn(state);
            return meta;
        }

        @Override
        public boolean setItemMeta(ItemMeta itemMeta) {
            if (!(itemMeta instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox state)) return false;
            contents = InventorySimulation.cloneContents(state.getInventory().getStorageContents());
            return true;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof TestShulkerStack shulker && Arrays.equals(contents, shulker.contents);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestShulkerStack shulker && amount == shulker.amount && isSimilar(shulker);
        }

        @Override
        public TestShulkerStack clone() {
            TestShulkerStack copy = new TestShulkerStack(contents);
            copy.amount = amount;
            return copy;
        }
    }

    private static void configureMeta(ItemMeta meta, boolean custom, String name) {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        if (custom) {
            when(pdc.getKeys()).thenReturn(Set.of(CUSTOM_HELMET_KEY));
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            when(meta.hasCustomModelData()).thenReturn(true);
            when(meta.hasDisplayName()).thenReturn(true);
            when(meta.getDisplayName()).thenReturn(name != null ? name : "Custom Item");
            when(meta.hasLore()).thenReturn(true);
        } else {
            when(pdc.getKeys()).thenReturn(Set.of());
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            when(meta.hasCustomModelData()).thenReturn(false);
            when(meta.hasDisplayName()).thenReturn(false);
            when(meta.hasLore()).thenReturn(false);
        }
        when(meta.hasAttributeModifiers()).thenReturn(false);
        when(meta.getItemFlags()).thenReturn(Set.of());
        when(meta.clone()).thenAnswer(inv -> {
            ItemMeta cloned = mock(ItemMeta.class);
            configureMeta(cloned, custom, name);
            return cloned;
        });
    }

    private static final class CustomItemProvider implements ItemProvider {
        @Override public String id() { return "custom"; }
        @Override public boolean ready() { return true; }

        @Override
        public ItemStack create(ItemSpec spec) {
            if ("custom_emerald".equalsIgnoreCase(spec.id())) {
                return createStack(Material.EMERALD, spec.amount(), true, "Custom Emerald");
            }
            if ("custom_diamond_helmet".equalsIgnoreCase(spec.id())) {
                return createStack(Material.DIAMOND_HELMET, spec.amount(), true, "Custom Diamond Helmet");
            }
            return null;
        }

        @Override
        public boolean matches(ItemStack stack, ItemSpec spec) {
            if (stack == null) return false;
            boolean identity = "custom_diamond_helmet".equalsIgnoreCase(spec.id()) && stack.getType() == Material.DIAMOND_HELMET
                || "custom_emerald".equalsIgnoreCase(spec.id()) && stack.getType() == Material.EMERALD;
            return identity && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().getKeys().contains(CUSTOM_HELMET_KEY);
        }

        @Override
        public Optional<String> identify(ItemStack stack) {
            if (stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().getKeys().contains(CUSTOM_HELMET_KEY)) {
                return Optional.of("custom_diamond_helmet");
            }
            return Optional.empty();
        }
    }

    private static int maxStackSize(Material material) {
        if (material.name().endsWith("_HELMET") || material.name().endsWith("_SWORD") || material.name().endsWith("_CHESTPLATE")
            || material.name().endsWith("_LEGGINGS") || material.name().endsWith("_BOOTS")) {
            return 1;
        }
        return 64;
    }

    private static void setBukkitServer(Server server) {
        try {
            java.lang.reflect.Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
