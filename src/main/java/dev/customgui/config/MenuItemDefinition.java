package dev.customgui.config;

import dev.customgui.recipe.ItemSpec;
import java.util.List;
import java.util.Map;

public record MenuItemDefinition(String id, ItemSpec icon, String name, List<String> lore,
                                 List<Integer> slots, int priority, String viewPermission,
                                 Map<String, String> clickPermissions, Map<String, List<String>> actions,
                                 boolean glow, Integer customModelData, boolean hideTooltip, List<String> itemFlags) {
    public MenuItemDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("menu item id is required");
        lore = List.copyOf(lore);
        slots = List.copyOf(slots);
        if (slots.isEmpty()) throw new IllegalArgumentException("menu item " + id + " has no slots");
        clickPermissions = Map.copyOf(clickPermissions);
        actions = actions.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            entry -> entry.getKey().toLowerCase(java.util.Locale.ROOT), entry -> List.copyOf(entry.getValue())));
        itemFlags = List.copyOf(itemFlags);
        var supportedClicks = java.util.Set.of("click", "left", "right", "shift-left", "shift-right", "middle");
        for (String click : clickPermissions.keySet()) if (!supportedClicks.contains(click))
            throw new IllegalArgumentException("unsupported click permission key: " + click);
        for (String click : actions.keySet()) if (!supportedClicks.contains(click))
            throw new IllegalArgumentException("unsupported action click key: " + click);
        if (customModelData != null && customModelData < 0) throw new IllegalArgumentException("custom-model-data cannot be negative");
        for (String flag : itemFlags) try { org.bukkit.inventory.ItemFlag.valueOf(flag.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("invalid item flag: " + flag); }
    }
}
