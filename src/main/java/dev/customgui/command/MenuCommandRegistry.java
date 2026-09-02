package dev.customgui.command;

import dev.customgui.config.ConfigSnapshot;
import dev.customgui.gui.GuiService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final java.util.Map<String, MenuCommand> registered = new LinkedHashMap<>();

    public MenuCommandRegistry(Server server, Supplier<ConfigSnapshot> snapshot, GuiService gui) {
        this.commandMap = server.getCommandMap(); this.snapshot = snapshot; this.gui = gui;
    }

    public List<String> replace() {
        var desired = new LinkedHashMap<String, String>();
        for (var menu : snapshot.get().menus().values()) for (String label : menu.openCommands()) desired.put(label, menu.id());
        for (String removed : registered.keySet().stream().filter(label -> !desired.containsKey(label)).toList())
            registered.remove(removed).unregister(commandMap);
        var conflicts = new ArrayList<String>();
        for (var entry : desired.entrySet()) {
            var existing = registered.get(entry.getKey());
            if (existing != null) { existing.menuId = entry.getValue(); continue; }
            var command = new MenuCommand(entry.getKey(), entry.getValue());
            commandMap.register("customgui", command);
            registered.put(entry.getKey(), command);
            if (commandMap.getCommand(entry.getKey()) != command) conflicts.add(entry.getKey());
        }
        return conflicts;
    }

    public void clear() { registered.values().forEach(command -> command.unregister(commandMap)); registered.clear(); }

    private final class MenuCommand extends Command {
        private String menuId;
        private MenuCommand(String name, String menuId) { super(name); this.menuId = menuId; setDescription("Open CustomGUI menu " + menuId); }

        @Override public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("This menu command can only be used by a player."); return true; }
            int page = 0;
            if (args.length > 0) try { page = Math.max(0, Integer.parseInt(args[0]) - 1); }
            catch (NumberFormatException ignored) { /* menu commands accept an optional page only */ }
            gui.open(player, menuId, page);
            return true;
        }

        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) return List.of("1", "2", "3").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            return List.of();
        }
    }
}
