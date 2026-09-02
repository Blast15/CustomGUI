package dev.customgui.recipe;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RecipeRegistry {
    private final Map<String, Recipe> recipes;

    public RecipeRegistry(Collection<Recipe> recipes) {
        this.recipes = recipes.stream().collect(Collectors.toUnmodifiableMap(Recipe::id, Function.identity()));
    }

    public Optional<Recipe> find(String id) { return Optional.ofNullable(recipes.get(id)); }
    public Collection<Recipe> all() { return recipes.values(); }
}

