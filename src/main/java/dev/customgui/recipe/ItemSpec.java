package dev.customgui.recipe;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record ItemSpec(String provider, String id, String itemType, int amount) {
    public ItemSpec {
        provider = normalize(provider);
        id = Objects.requireNonNull(id, "id");
        itemType = itemType == null ? "" : itemType;
        if (amount < 1) throw new IllegalArgumentException("amount must be positive");
    }

    public static ItemSpec from(Map<?, ?> map) {
        var provider = text(map, "provider", "vanilla");
        var id = provider.equalsIgnoreCase("vanilla") ? text(map, "material", null) : text(map, "id", null);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("item id/material is required");
        return new ItemSpec(provider, id, text(map, "item-type", text(map, "type", "")), integer(map, "amount", 1));
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "provider").toLowerCase(Locale.ROOT);
    }

    static String text(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    static int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) return ((Number) value).intValue();
        if (value instanceof Long number) try { return Math.toIntExact(number.longValue()); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(key + " is outside the integer range", ex); }
        if (value instanceof java.math.BigInteger number) try { return number.intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(key + " is outside the integer range", ex); }
        if (value instanceof Number number) {
            double exact = number.doubleValue();
            if (!Double.isFinite(exact) || exact != Math.rint(exact) || exact < Integer.MIN_VALUE || exact > Integer.MAX_VALUE)
                throw new IllegalArgumentException(key + " must be an integer in range");
            return (int) exact;
        }
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be an integer"); }
    }
}

