package dev.customgui.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryPlannerTest {
    @Test void combinesStacksExactly() {
        assertEquals(List.of(new InventoryPlanner.Removal(0, 2), new InventoryPlanner.Removal(2, 3)),
            InventoryPlanner.plan(new int[]{2, 50, 4}, 5, slot -> slot != 1));
    }
    @Test void returnsNoPartialPlan() {
        assertEquals(List.of(), InventoryPlanner.plan(new int[]{2, 2}, 5, slot -> true));
    }
}
