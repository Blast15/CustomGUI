package dev.customgui.command;

import dev.customgui.config.ConfigSnapshot;
import dev.customgui.gui.GuiService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class MenuCommandRegistry {
    private final CommandMap commandMap;
    private final Supplier<ConfigSnapshot> snapshot;
    private final GuiService gui;
    private Map<String, MenuCommand> registered = Map.of();
    private Map<String, String> registeredMenus = Map.of();

    public MenuCommandRegistry(Server server, Supplier<ConfigSnapshot> snapshot, GuiService gui) {
        this.commandMap = server.getCommandMap(); this.snapshot = snapshot; this.gui = gui;
    }

    public CommandReplacementPlan plan(ConfigSnapshot candidate) {
        var desired = new LinkedHashMap<String, String>();
        for (var menu : candidate.menus().values()) for (String label : menu.openCommands()) {
            if (!label.matches("[a-z0-9][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("invalid command label: " + label);
            String previous = desired.putIfAbsent(label, menu.id());
            if (previous != null) throw new IllegalArgumentException("duplicate command label " + label);
            Command occupying = commandMap.getCommand(label);
            if (occupying != null && occupying != registered.get(label))
                throw new IllegalArgumentException("command label is already registered: " + label);
        }
        return new CommandReplacementPlan(Map.copyOf(desired));
    }

    public void commit(CommandReplacementPlan plan) {
        // Re-registering an identical set of legacy Bukkit commands is not a no-op on
        // modern Paper: the command dispatcher can retain the just-unregistered labels
        // until its next sync, causing registration and then rollback to fail. Menu
        // commands resolve GUI content through the live snapshot, so keeping the
        // existing command objects is both sufficient and safer for ordinary reloads.
        if (registeredMenus.equals(plan.commands())) return;
        if (!registered.isEmpty())
            throw new IllegalArgumentException("menu open-commands cannot be changed during reload; restart the server");
        Map<String, MenuCommand> previous = registered;
        Map<String, String> previousMenus = registeredMenus;
        var replacement = new LinkedHashMap<String, MenuCommand>();
        try {
            previous.values().forEach(this::unregister);
            for (var entry : plan.commands().entrySet()) {
                var command = new MenuCommand(entry.getKey(), entry.getValue());
                if (!commandMap.register("customgui", command) || commandMap.getCommand(entry.getKey()) != command)
                    throw new IllegalStateException("could not register command " + entry.getKey());
                replacement.put(entry.getKey(), command);
            }
            registered = Map.copyOf(replacement);
            registeredMenus = plan.commands();
        } catch (RuntimeException | LinkageError failure) {
            replacement.values().forEach(this::unregister);
            var rollbackFailures = new java.util.ArrayList<String>();
            for (var entry : previous.entrySet())
                if (!commandMap.register("customgui", entry.getValue()) || commandMap.getCommand(entry.getKey()) != entry.getValue())
                    rollbackFailures.add(entry.getKey());
            registered = previous;
            registeredMenus = previousMenus;
            if (!rollbackFailures.isEmpty()) throw new IllegalStateException("command rollback failed: " + rollbackFailures, failure);
            throw failure;
        }
    }

    public List<String> replace() { commit(plan(snapshot.get())); return List.of(); }
    public void clear() { registered.values().forEach(this::unregister); registered = Map.of(); registeredMenus = Map.of(); }

    private void unregister(MenuCommand command) {
        command.unregister(commandMap);
    }

    public record CommandReplacementPlan(Map<String, String> commands) {
        public CommandReplacementPlan { commands = Map.copyOf(commands); }
    }

    private final class MenuCommand extends Command {
        private final String menuId;
        private MenuCommand(String name, String menuId) { super(name); this.menuId = menuId; setDescription("Open CustomGUI menu " + menuId); }

        @Override public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("This menu command can only be used by a player."); return true; }
            int page = 0;
            if (args.length > 0) try { page = Math.max(0, Integer.parseInt(args[0]) - 1); }
            catch (NumberFormatException ignored) { /* optional page */ }
            gui.open(player, menuId, page);
            return true;
        }

        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) return List.of("1", "2", "3").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            return List.of();
        }
    }
}
