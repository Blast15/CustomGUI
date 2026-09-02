package dev.customgui.transaction;

import java.util.function.IntPredicate;

public final class BatchSearch {
    private BatchSearch() {}
    public static int highestFeasible(int maximum, IntPredicate feasible) {
        int low = 1, high = maximum, found = 0;
        while (low <= high) {
            int candidate = low + (high - low) / 2;
            if (feasible.test(candidate)) { found = candidate; low = candidate + 1; }
            else high = candidate - 1;
        }
        return found;
    }
}
