package dev.customgui.recipe;

import java.util.Map;

public record RequirementSpec(String type, Map<String, Object> values) {
    public RequirementSpec {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("requirement type is required");
        values = Map.copyOf(values);
    }
}

