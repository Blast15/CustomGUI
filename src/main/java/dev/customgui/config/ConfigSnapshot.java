package dev.customgui.config;

import dev.customgui.recipe.RecipeRegistry;
import java.util.Map;

public record ConfigSnapshot(long revision, Map<String, MenuDefinition> menus, RecipeRegistry recipes,
                             Map<String, String> messages, Map<String, String> invalidRecipes,
                             boolean allowPlayerInventoryInteraction, int maxBatchSize) {
    public ConfigSnapshot { menus = Map.copyOf(menus); messages = Map.copyOf(messages); invalidRecipes = Map.copyOf(invalidRecipes); }
}
