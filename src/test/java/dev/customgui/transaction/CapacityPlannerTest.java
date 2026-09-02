package dev.customgui.transaction;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CapacityPlannerTest {
    @Test void countsPartialAndEmptyCapacity() {
        assertTrue(CapacityPlanner.canFit(new int[]{60}, new int[]{64}, 1, 64, 68));
        assertFalse(CapacityPlanner.canFit(new int[]{64}, new int[]{64}, 0, 64, 1));
    }
    @Test void supportsUnstackableItemsAndLargeAmountsWithoutOverflow() {
        assertTrue(CapacityPlanner.canFit(new int[]{1}, new int[]{1}, 2, 1, 2));
        assertTrue(CapacityPlanner.canFit(new int[0], new int[0], Integer.MAX_VALUE, 64, Integer.MAX_VALUE));
    }
}
