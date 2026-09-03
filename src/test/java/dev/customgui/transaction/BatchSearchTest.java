package dev.customgui.transaction;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BatchSearchTest {
    @Test void returnsHighestFeasibleForMonotonicInput() {
        var checks = new AtomicInteger();
        assertEquals(3071, BatchSearch.highestFeasible(4096, value -> { checks.incrementAndGet(); return value <= 3071; }));
        assertEquals(1026, checks.get());
    }

    @Test void supportsNonMonotonicFeasibility() {
        assertEquals(64, BatchSearch.highestFeasible(64, value -> value == 64));
    }
}
