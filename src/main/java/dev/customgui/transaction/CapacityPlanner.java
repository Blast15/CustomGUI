package dev.customgui.transaction;

public final class CapacityPlanner {
    private CapacityPlanner() {}
    public static boolean canFit(int[] similarAmounts, int[] similarMaximums, int emptySlots, int newStackMaximum, int outputAmount) {
        if (similarAmounts.length != similarMaximums.length || emptySlots < 0 || newStackMaximum < 1 || outputAmount < 0)
            throw new IllegalArgumentException("invalid capacity input");
        long capacity = (long) emptySlots * newStackMaximum;
        for (int index = 0; index < similarAmounts.length; index++) {
            if (similarAmounts[index] < 0 || similarMaximums[index] < similarAmounts[index]) throw new IllegalArgumentException("invalid stack amount");
            capacity += similarMaximums[index] - similarAmounts[index];
        }
        return capacity >= outputAmount;
    }
}
