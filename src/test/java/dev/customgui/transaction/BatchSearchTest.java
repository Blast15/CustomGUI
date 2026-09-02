package dev.customgui.transaction;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BatchSearchTest {
    @Test void batch4096UsesLogarithmicFeasibilityChecks() {
        var checks = new AtomicInteger();
        assertEquals(3071, BatchSearch.highestFeasible(4096, value -> { checks.incrementAndGet(); return value <= 3071; }));
        assertTrue(checks.get() <= 13, "checks=" + checks.get());
    }
}
