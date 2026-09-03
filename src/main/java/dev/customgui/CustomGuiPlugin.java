package dev.customgui;

import dev.customgui.config.ConfigLoader;
import dev.customgui.config.ConfigSnapshot;
import dev.customgui.config.MessageService;
import dev.customgui.config.ReloadCoordinator;
import dev.customgui.event.GuiListener;
import dev.customgui.gui.GuiService;
import dev.customgui.gui.SessionRegistry;
import dev.customgui.integration.item.ItemProviderRegistry;
import dev.customgui.integration.item.VanillaItemProvider;
import dev.customgui.integration.item.ReflectiveItemProvider;
import dev.customgui.integration.item.MmoItemsProvider;
import dev.customgui.integration.item.ExtendedItemProvider;
import dev.customgui.integration.item.TemplateItemProvider;
import dev.customgui.integration.item.ItemEditProvider;
import dev.customgui.integration.economy.EconomyBridge;
import dev.customgui.integration.enchant.CrazyEnchantmentsBridge;
import dev.customgui.integration.placeholder.PlaceholderBridge;
import dev.customgui.transaction.PlayerTransactionExecutor;
import dev.customgui.api.CustomGuiApi;
import dev.customgui.api.CustomGuiApiImpl;
import dev.customgui.command.MenuCommandRegistry;
import dev.customgui.editor.EditorListener;
import dev.customgui.editor.EditorService;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class CustomGuiPlugin extends JavaPlugin {
    private final AtomicReference<ConfigSnapshot> snapshot = new AtomicReference<>();
    private final SessionRegistry sessions = new SessionRegistry();
    private ItemProviderRegistry providers;
    private PlayerTransactionExecutor transactions;
    private GuiService gui;
    private MessageService messages;
    private MenuCommandRegistry menuCommands;
    private EditorService editor;
    private TemplateItemProvider templates;
    private ReloadCoordinator reloads;
    private CrazyEnchantmentsBridge crazyEnchantments;
    private PlaceholderBridge placeholderBridge;
    private boolean runtimeReady;

    @Override public void onEnable() {
        saveDefaults();
        try { snapshot.set(new ConfigLoader().load(getDataFolder(), 1)); }
        catch (RuntimeException ex) { getLogger().severe("Configuration invalid: " + ex.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        messages = new MessageService(snapshot::get);
        providers = new ItemProviderRegistry();
        providers.register(new VanillaItemProvider());
        templates = new TemplateItemProvider(getDataFolder().toPath());
        providers.register(templates);
        registerExternalProviders();
        crazyEnchantments = CrazyEnchantmentsBridge.discover(getServer());
        placeholderBridge = PlaceholderBridge.discover(getServer());
        getServer().getServicesManager().register(CustomGuiApi.class, new CustomGuiApiImpl(providers, snapshot::get, () -> gui), this,
            org.bukkit.plugin.ServicePriority.Normal);
        // Dependent addons register API providers in onEnable, so final validation must happen after plugin startup.
        getServer().getScheduler().runTask(this, this::finishEnable);
    }

    private void finishEnable() {
        if (!isEnabled()) return;
        try { providers.validate(snapshot.get()); }
        catch (RuntimeException ex) { getLogger().severe("Configured item provider validation failed: " + ex.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        try { CrazyEnchantmentsBridge.validate(snapshot.get(), crazyEnchantments); }
        catch (RuntimeException ex) { getLogger().severe("CrazyEnchantments validation failed: " + ex.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        transactions = new PlayerTransactionExecutor(providers, EconomyBridge.discover(getServer()), placeholderBridge, crazyEnchantments,
            () -> snapshot.get().maxBatchSize(), message -> getLogger().severe(message));
        gui = new GuiService(snapshot::get, sessions, providers, transactions, placeholderBridge, messages);
        menuCommands = new MenuCommandRegistry(getServer(), snapshot::get, gui);
        try { menuCommands.commit(menuCommands.plan(snapshot.get())); }
        catch (RuntimeException ex) { getLogger().severe("Command registration failed: " + ex.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        reloads = new ReloadCoordinator(getDataFolder(), snapshot, menuCommands, sessions, providers, crazyEnchantments,
            message -> getLogger().severe(message));
        editor = new EditorService(this, snapshot::get, this::reloadSnapshot);
        getServer().getPluginManager().registerEvents(new GuiListener(gui, sessions, transactions), this);
        getServer().getPluginManager().registerEvents(new EditorListener(editor), this);
        runtimeReady = true;
        getLogger().info("CustomGUI " + getPluginMeta().getVersion() + " | Paper " + getServer().getMinecraftVersion()
            + " | Java " + Runtime.version().feature() + " | " + snapshot.get().menus().size() + " menus, "
            + snapshot.get().recipes().all().size() + " recipes, " + snapshot.get().invalidRecipes().size() + " invalid | " + providers.statuses());
    }

    @Override public void onDisable() {
        runtimeReady = false;
        if (transactions != null) transactions.shutdown();
        sessions.invalidateAll();
        if (editor != null) editor.shutdown();
        if (menuCommands != null) menuCommands.clear();
        if (providers != null) providers.invalidateAll();
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
    if (!runtimeReady) { sender.sendMessage(net.kyori.adventure.text.Component.text("CustomGUI is still starting.")); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sender.sendMessage(messages.render("usage")); return true; }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("customgui.admin")) return denied(sender);
            reloadSnapshotAsync(error -> {
                if (error == null) {
                    var replacement = snapshot.get();
                    sender.sendMessage(messages.render("reload-success", java.util.Map.of("menus", Integer.toString(replacement.menus().size()), "recipes", Integer.toString(replacement.recipes().all().size()))));
                } else sender.sendMessage(messages.render("reload-failed", java.util.Map.of("error", error)));
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("editor")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(messages.render("player-required")); return true; }
            if (!player.hasPermission("customgui.editor")) return denied(sender);
            editor.open(player); return true;
        }
        if (args[0].equalsIgnoreCase("capture")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(messages.render("player-required")); return true; }
            if (!player.hasPermission("customgui.admin")) return denied(sender);
            if (args.length < 2) { sender.sendMessage(messages.render("capture-usage")); return true; }
            try {
                boolean replace = args.length >= 3 && args[2].equalsIgnoreCase("replace");
                var result = templates.capture(args[1], player.getInventory().getItemInMainHand(), replace);
                sender.sendMessage(messages.render("capture-success", java.util.Map.of("item", args[1].toLowerCase(java.util.Locale.ROOT),
                    "mode", result.name().toLowerCase(java.util.Locale.ROOT), "count", Integer.toString(templates.size()))));
            } catch (IOException | IllegalArgumentException ex) {
                sender.sendMessage(messages.render("capture-failed", java.util.Map.of("error", ex.getMessage())));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("open") && args.length >= 2) {
            Player viewer;
            int pageArgument;
            if (args.length >= 3) {
                if (!sender.hasPermission("customgui.open.others")) return denied(sender);
                viewer = getServer().getPlayerExact(args[2]); pageArgument = 3;
                if (viewer == null) { sender.sendMessage(messages.render("unknown-player", java.util.Map.of("player", args[2]))); return true; }
            } else if (sender instanceof Player player) {
                if (!player.hasPermission("customgui.open")) return denied(sender);
                viewer = player; pageArgument = 2;
            } else { sender.sendMessage(messages.render("player-required")); return true; }
            int page = 0;
            if (args.length > pageArgument) try { page = Math.max(0, Integer.parseInt(args[pageArgument]) - 1); }
            catch (NumberFormatException ex) { sender.sendMessage(messages.render("invalid-page")); return true; }
            if (!gui.open(viewer, args[1], page)) sender.sendMessage(messages.render("unknown-menu", java.util.Map.of("menu", args[1])));
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("customgui.open")) return denied(sender);
            sender.sendMessage(messages.render("menu-list", java.util.Map.of("menus", String.join(", ", snapshot.get().menus().keySet().stream().sorted().toList()))));
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(messages.render("info", java.util.Map.of("menus", Integer.toString(snapshot.get().menus().size()),
                "recipes", Integer.toString(snapshot.get().recipes().all().size()), "invalid", Integer.toString(snapshot.get().invalidRecipes().size()))));
            return true;
        }
        if (args[0].equalsIgnoreCase("providers")) {
            if (!sender.hasPermission("customgui.admin")) return denied(sender);
            sender.sendMessage(messages.render("providers", java.util.Map.of("providers", providers.statuses().toString())));
            return true;
        }
        sender.sendMessage(messages.render("usage"));
        return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!runtimeReady) return List.of();
        if (args.length == 1) return List.of("open", "editor", "capture", "list", "info", "providers", "reload", "help").stream()
            .filter(s -> !s.equals("editor") || sender.hasPermission("customgui.editor"))
            .filter(s -> !List.of("capture", "providers", "reload").contains(s) || sender.hasPermission("customgui.admin"))
            .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("capture")) return List.of("replace").stream().filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) return snapshot.get().menus().keySet().stream()
            .filter(id -> gui.canOpen(sender, id)).filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).sorted().toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("open") && sender.hasPermission("customgui.open.others"))
            return getServer().getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase())).sorted().toList();
        return List.of();
    }

    private boolean denied(CommandSender sender) { sender.sendMessage(messages.render("no-permission")); return true; }

    private String reloadSnapshot() {
        var result = reloads.reload();
        if (result.success()) getLogger().info("Reloaded " + result.snapshot().menus().size() + " menus and "
            + result.snapshot().recipes().all().size() + " recipes at revision " + result.snapshot().revision());
        return result.error();
    }

    private void reloadSnapshotAsync(java.util.function.Consumer<String> completion) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            var prepared = reloads.prepare();
            getServer().getScheduler().runTask(this, () -> {
                var result = reloads.apply(prepared);
                if (result.success()) getLogger().info("Reloaded " + result.snapshot().menus().size() + " menus and "
                    + result.snapshot().recipes().all().size() + " recipes at revision " + result.snapshot().revision());
                completion.accept(result.error());
            });
        });
    }

    private void registerExternalProviders() {
        register("ItemsAdder", plugin -> new ReflectiveItemProvider("itemsadder", plugin, ReflectiveItemProvider.Api.ITEMSADDER));
        register("Oraxen", plugin -> new ReflectiveItemProvider("oraxen", plugin, ReflectiveItemProvider.Api.ORAXEN));
        register("Nexo", plugin -> new ReflectiveItemProvider("nexo", plugin, ReflectiveItemProvider.Api.NEXO));
        register("MMOItems", MmoItemsProvider::new);
        register("ItemEdit", ItemEditProvider::new);
        registerExtended("ecoitems", "EcoItems", ExtendedItemProvider.Api.ECO_ITEMS);
        registerExtended("slimefun", "Slimefun", ExtendedItemProvider.Api.SLIMEFUN);
        registerExtended("mythicmobs", "MythicMobs", ExtendedItemProvider.Api.MYTHIC_MOBS);
        registerExtended("nova", "Nova", ExtendedItemProvider.Api.NOVA);
        registerExtended("executableitems", "SCore", ExtendedItemProvider.Api.EXECUTABLE_ITEMS, "ExecutableItems");
        registerExtended("mythiccrucible", "MythicMobs", ExtendedItemProvider.Api.MYTHIC_MOBS, "MythicCrucible");
    }

    private void register(String pluginName, java.util.function.Function<org.bukkit.plugin.Plugin, dev.customgui.integration.item.ItemProvider> factory) {
        var plugin = getServer().getPluginManager().getPlugin(pluginName);
        if (plugin != null && plugin.isEnabled()) providers.register(factory.apply(plugin));
    }

    private void registerExtended(String id, String apiPluginName, ExtendedItemProvider.Api api, String... requiredPluginNames) {
        var apiPlugin = getServer().getPluginManager().getPlugin(apiPluginName);
        if (apiPlugin == null || !apiPlugin.isEnabled()) return;
        var required = new java.util.ArrayList<org.bukkit.plugin.Plugin>();
        for (String name : requiredPluginNames) {
            var plugin = getServer().getPluginManager().getPlugin(name);
            if (plugin == null || !plugin.isEnabled()) return;
            required.add(plugin);
        }
        providers.register(new ExtendedItemProvider(id, apiPlugin, api, required.toArray(org.bukkit.plugin.Plugin[]::new)));
    }

    private void saveDefaults() {
        saveDefaultConfig();
        saveResourceIfMissing("menus/showcase.yml");
        saveResourceIfMissing("menus/upgrades.yml");
        saveResourceIfMissing("menus/exchange.yml");
        saveResourceIfMissing("recipes/vanilla.yml");
    }

    private void saveResourceIfMissing(String path) {
        if (!new File(getDataFolder(), path).isFile()) saveResource(path, false);
    }
}
