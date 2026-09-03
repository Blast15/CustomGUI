package dev.customgui.integration.enchant;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class EnchantmentSpec {
    private EnchantmentSpec() {}

    public static Map<String, Integer> from(Map<String, Object> values) {
        Object configured = values.get("crazy-enchantments");
        if (configured == null) return Map.of();
        if (!(configured instanceof Map<?, ?> raw)) throw new IllegalArgumentException("crazy-enchantments must be a map of name: level");
        var result = new LinkedHashMap<String, Integer>();
        raw.forEach((key, value) -> {
            String name = String.valueOf(key).trim().toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid CrazyEnchantments name: " + key);
            int level;
            try { level = value instanceof Number number ? Math.toIntExact(number.longValue()) : Integer.parseInt(String.valueOf(value)); }
            catch (ArithmeticException | NumberFormatException ex) { throw new IllegalArgumentException("invalid level for CrazyEnchantments " + key); }
            if (level < 1 || level > 255) throw new IllegalArgumentException("CrazyEnchantments level must be 1..255: " + key);
            if (result.putIfAbsent(name, level) != null) throw new IllegalArgumentException("duplicate CrazyEnchantments name: " + key);
        });
        return Map.copyOf(result);
    }
}
