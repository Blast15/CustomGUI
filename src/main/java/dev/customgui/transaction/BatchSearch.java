package dev.customgui.transaction;

import java.util.function.IntPredicate;

public final class BatchSearch {
    private BatchSearch() {}
    public static int highestFeasible(int maximum, IntPredicate feasible) {
        // ponytail: capped linear fallback; derive inventory-capacity breakpoints if profiling shows this is hot.
        for (int candidate = maximum; candidate >= 1; candidate--) if (feasible.test(candidate)) return candidate;
        return 0;
    }
}
