package dev.customgui.recipe;

import java.util.Map;

public record ResultSpec(String type, Map<String, Object> values) {
    public ResultSpec {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("result type is required");
        values = Map.copyOf(values);
    }
}

