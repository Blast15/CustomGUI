package dev.customgui.config;

import java.util.List;
import java.util.Map;

public record MenuDefinition(String id, String title, int rows, String permission, List<String> openCommands,
                             List<Integer> contentSlots, List<String> recipeGroups, List<String> recipeCategories,
                             Map<String, String> recipeActions, String recipeName, List<String> recipeLore,
                             List<MenuItemDefinition> items) {
    public MenuDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("invalid menu id: " + id);
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("menu rows must be 1..6");
        title = title == null ? id : title;
        permission = permission == null ? "" : permission;
        openCommands = List.copyOf(openCommands);
        contentSlots = List.copyOf(contentSlots);
        recipeGroups = List.copyOf(recipeGroups);
        recipeCategories = List.copyOf(recipeCategories);
        recipeActions = Map.copyOf(recipeActions);
        recipeName = recipeName == null ? "<yellow>%recipe_id%</yellow>" : recipeName;
        recipeLore = List.copyOf(recipeLore);
        items = items.stream().sorted(java.util.Comparator.comparingInt(MenuItemDefinition::priority).reversed()).toList();
    }
}
