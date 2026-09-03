package dev.customgui.recipe;

import java.util.Set;

public final class RecipeValidator {
    private static final Set<String> REQUIREMENTS = Set.of("item", "money", "currency", "permission", "level", "experience", "world", "game-mode", "placeholder", "chance");
    private RecipeValidator() {}

    public static Recipe validate(Recipe recipe) {
        for (var requirement : recipe.requirements()) {
            String type = requirement.type().toLowerCase(java.util.Locale.ROOT);
            if (!REQUIREMENTS.contains(type)) throw new IllegalArgumentException("unsupported requirement: " + type);
            var values = requirement.values();
            switch (type) {
                case "item" -> {
                    ItemSpec.from(values);
                    dev.customgui.integration.enchant.EnchantmentSpec.from(values);
                    if (values.containsKey("consume")) strictBoolean(values.get("consume"), "consume");
                }
                case "money", "currency" -> positiveDecimal(values.get("amount"), "amount");
                case "permission" -> required(values.get("permission"), "permission");
                case "level" -> nonNegative(values.getOrDefault("amount", values.get("min-level")), "level");
                case "experience" -> nonNegative(values.get("amount"), "amount");
                case "world" -> required(values.get("world"), "world");
                case "game-mode" -> required(values.get("game-mode"), "game-mode");
                case "chance" -> { double chance = positiveDecimal(values.get("chance"), "chance"); if (chance > 1) throw new IllegalArgumentException("chance must be <= 1"); }
                case "placeholder" -> {
                    required(values.get("placeholder"), "placeholder"); required(values.get("operator"), "operator"); required(values.get("value"), "value");
                }
                default -> throw new IllegalStateException(type);
            }
        }
        for (var result : recipe.results()) {
            if (!result.type().equalsIgnoreCase("give-item")) throw new IllegalArgumentException("unsupported result: " + result.type());
            ItemSpec.from(result.values());
            dev.customgui.integration.enchant.EnchantmentSpec.from(result.values());
        }
        return recipe;
    }

    private static String required(Object value, String key) {
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required"); return String.valueOf(value);
    }
    private static int nonNegative(Object value, String key) {
        int number = ItemSpec.exactInteger(value, key);
        if (number < 0) throw new IllegalArgumentException(key + " must be non-negative"); return number;
    }
    private static boolean strictBoolean(Object value, String key) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")))
            return Boolean.parseBoolean(text);
        throw new IllegalArgumentException(key + " must be true or false");
    }
    private static double positiveDecimal(Object value, String key) {
        double number; try { number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be decimal"); }
        if (!Double.isFinite(number) || number <= 0) throw new IllegalArgumentException(key + " must be finite and positive"); return number;
    }
}
