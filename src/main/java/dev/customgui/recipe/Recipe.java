package dev.customgui.recipe;

import java.util.List;

public record Recipe(String id, String group, String category, boolean enabled,
                     List<RequirementSpec> requirements, List<ResultSpec> results) {
    public Recipe {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("invalid recipe id: " + id);
        group = group == null ? "default" : group;
        category = category == null ? "default" : category;
        requirements = List.copyOf(requirements);
        results = List.copyOf(results);
        if (results.isEmpty()) throw new IllegalArgumentException("recipe must have at least one result");
    }
}

