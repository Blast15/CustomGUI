package dev.customgui.integration.enchant;

import dev.customgui.config.ConfigSnapshot;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Optional bridge to the documented CrazyEnchantments manager API. */
public final class CrazyEnchantmentsBridge {
    private final Plugin plugin;
    private final Object manager;
    private final Object settings;
    private final Method find;
    private final Method enchantments;
    private final Method add;

    private CrazyEnchantmentsBridge(Plugin plugin, Object manager, Object settings, Method find, Method enchantments, Method add) {
        this.plugin = plugin; this.manager = manager; this.settings = settings;
        this.find = find; this.enchantments = enchantments; this.add = add;
    }

    public static CrazyEnchantmentsBridge discover(Server server) {
        Plugin plugin = server.getPluginManager().getPlugin("CrazyEnchantments");
        if (plugin == null || !plugin.isEnabled()) return null;
        try {
            Object starter = plugin.getClass().getMethod("getStarter").invoke(plugin);
            Object manager = starter.getClass().getMethod("getCrazyManager").invoke(starter);
            Object settings = starter.getClass().getMethod("getEnchantmentBookSettings").invoke(starter);
            Method find = manager.getClass().getMethod("getEnchantmentFromName", String.class);
            Class<?> enchantment = find.getReturnType();
            return new CrazyEnchantmentsBridge(plugin, manager, settings, find,
                settings.getClass().getMethod("getEnchantments", ItemStack.class),
                manager.getClass().getMethod("addEnchantment", ItemStack.class, enchantment, int.class));
        } catch (ReflectiveOperationException | LinkageError ex) { return null; }
    }

    public boolean matches(ItemStack stack, Map<String, Object> values) {
        Map<String, Integer> required = EnchantmentSpec.from(values);
        if (required.isEmpty()) return true;
        ensureReady();
        try {
            @SuppressWarnings("unchecked") Map<Object, Integer> present = (Map<Object, Integer>) enchantments.invoke(settings, stack);
            for (var entry : required.entrySet()) {
                Object enchantment = lookup(entry.getKey());
                if (present.getOrDefault(enchantment, 0) < entry.getValue()) return false;
            }
            return true;
        } catch (ReflectiveOperationException | ClassCastException ex) { throw new IllegalStateException("CrazyEnchantments read failed", ex); }
    }

    public ItemStack apply(ItemStack source, Map<String, Object> values) {
        Map<String, Integer> configured = EnchantmentSpec.from(values);
        if (configured.isEmpty()) return source;
        ensureReady();
        ItemStack result = source.clone();
        try {
            for (var entry : configured.entrySet()) add.invoke(manager, result, lookup(entry.getKey()), entry.getValue());
            return result;
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("CrazyEnchantments mutation failed", ex); }
    }

    public static void validate(ConfigSnapshot snapshot, CrazyEnchantmentsBridge bridge) {
        for (var recipe : snapshot.recipes().all()) {
            for (var requirement : recipe.requirements()) validate(requirement.values(), bridge);
            for (var result : recipe.results()) validate(result.values(), bridge);
        }
    }

    private static void validate(Map<String, Object> values, CrazyEnchantmentsBridge bridge) {
        Map<String, Integer> configured = EnchantmentSpec.from(values);
        if (!configured.isEmpty() && bridge == null) throw new IllegalArgumentException("CrazyEnchantments is required by configured recipe");
        for (var entry : configured.entrySet()) {
            Object enchantment = bridge.lookup(entry.getKey());
            try {
                int maximum = ((Number) enchantment.getClass().getMethod("getMaxLevel").invoke(enchantment)).intValue();
                if (entry.getValue() > maximum) throw new IllegalArgumentException(entry.getKey() + " exceeds CrazyEnchantments max level " + maximum);
            } catch (ReflectiveOperationException | ClassCastException ex) { throw new IllegalStateException("CrazyEnchantments validation failed", ex); }
        }
    }

    private Object lookup(String name) {
        ensureReady();
        try {
            Object value = find.invoke(manager, name);
            if (value == null) throw new IllegalArgumentException("unknown CrazyEnchantments enchantment: " + name);
            return value;
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("CrazyEnchantments lookup failed", ex); }
    }

    private void ensureReady() {
        if (!plugin.isEnabled()) throw new IllegalStateException("CrazyEnchantments is unavailable");
    }
}
