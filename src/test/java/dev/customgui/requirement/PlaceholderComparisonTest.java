package dev.customgui.requirement;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PlaceholderComparisonTest {
    @Test void comparesTypedNumbersNotStrings() {
        assertTrue(PlaceholderComparison.test("10", "2", ">", "integer"));
        assertTrue(PlaceholderComparison.test("10.00", "10", "==", "decimal"));
    }
    @Test void supportsStringsAndRejectsUnsafeRegex() {
        assertTrue(PlaceholderComparison.test("dragon_blade", "dragon", "starts-with", "string"));
        assertThrows(IllegalArgumentException.class, () -> PlaceholderComparison.test("aaaa", "(a+)+", "matches", "string"));
    }
}
