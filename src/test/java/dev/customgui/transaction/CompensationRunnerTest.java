package dev.customgui.transaction;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CompensationRunnerTest {
    @Test void economyCompensationStillRunsWhenInventoryRestoreThrows() {
        var economyRan = new AtomicBoolean();
        var report = CompensationRunner.run(true, () -> { throw new IllegalStateException("inventory"); },
            true, () -> economyRan.set(true));
        assertTrue(economyRan.get());
        assertFalse(report.inventoryRestored());
        assertTrue(report.economyRestored());
        assertNotNull(report.inventoryFailure());
    }

    @Test void reportsBothCompensationFailures() {
        var report = CompensationRunner.run(true, () -> { throw new Error("inventory"); },
            true, () -> { throw new IllegalStateException("economy"); });
        assertFalse(report.complete());
        assertNotNull(report.inventoryFailure());
        assertNotNull(report.economyFailure());
    }
}
