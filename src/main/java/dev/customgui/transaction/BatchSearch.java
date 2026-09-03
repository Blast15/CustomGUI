package dev.customgui.transaction;

import java.util.function.IntPredicate;

public final class BatchSearch {
    private BatchSearch() {}
    public static int highestFeasible(int maximum, IntPredicate feasible) {
        if (maximum < 0) throw new IllegalArgumentException("maximum must be non-negative");
        // Inventory feasibility is not monotonic: a larger batch can empty an input
        // stack and free a slot that a smaller batch cannot. Search from the bounded
        // maximum so the first match is always the true largest feasible batch.
        for (int candidate = maximum; candidate > 0; candidate--)
            if (feasible.test(candidate)) return candidate;
        return 0;
    }
}
