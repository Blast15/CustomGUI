package dev.customgui.integration.enchant;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnchantmentSpecTest {
    @Test void parsesAndNormalizesConfiguredEnchantments() {
        var parsed = EnchantmentSpec.from(Map.of("crazy-enchantments", Map.of("Rage", 3, "LIFESTEAL", "2")));
        assertEquals(Map.of("rage", 3, "lifesteal", 2), parsed);
    }

    @Test void rejectsInvalidNamesAndLevels() {
        assertThrows(IllegalArgumentException.class,
            () -> EnchantmentSpec.from(Map.of("crazy-enchantments", Map.of("../rage", 1))));
        assertThrows(IllegalArgumentException.class,
            () -> EnchantmentSpec.from(Map.of("crazy-enchantments", Map.of("rage", 0))));
    }
}
