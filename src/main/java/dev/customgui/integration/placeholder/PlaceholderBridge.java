package dev.customgui.integration.placeholder;

import java.lang.reflect.Method;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PlaceholderBridge {
    private final Plugin plugin;
    private final Method setPlaceholders;
    private PlaceholderBridge(Plugin plugin, Method method) { this.plugin = plugin; this.setPlaceholders = method; }

    public static PlaceholderBridge discover(org.bukkit.Server server) {
        Plugin plugin = server.getPluginManager().getPlugin("PlaceholderAPI");
        if (plugin == null || !plugin.isEnabled()) return null;
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, plugin.getClass().getClassLoader());
            return new PlaceholderBridge(plugin, api.getMethod("setPlaceholders", Player.class, String.class));
        } catch (ReflectiveOperationException | LinkageError ex) { return null; }
    }

    public String parse(Player player, String placeholder) {
        if (!plugin.isEnabled()) throw new IllegalStateException("PlaceholderAPI unavailable");
        try { return String.valueOf(setPlaceholders.invoke(null, player, placeholder)); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("PlaceholderAPI invocation failed", ex); }
    }
}
