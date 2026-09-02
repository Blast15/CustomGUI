package dev.customgui.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotParserTest {
    @Test void parsesNumbersRangesAndLists() {
        assertEquals(List.of(5, 7, 8, 9), SlotParser.parse(List.of(5, "7-9"), 54));
    }
    @Test void rejectsBadAndOutOfBoundsSlots() {
        assertThrows(IllegalArgumentException.class, () -> SlotParser.parse("9-5", 54));
        assertThrows(IllegalArgumentException.class, () -> SlotParser.parse(54, 54));
    }
}

