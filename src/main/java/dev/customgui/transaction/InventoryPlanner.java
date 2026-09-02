package dev.customgui.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class InventoryPlanner {
    private InventoryPlanner() {}

    public static List<Removal> plan(int[] amounts, int required, Predicate<Integer> matchesSlot) {
        if (required < 1) throw new IllegalArgumentException("required must be positive");
        var removals = new ArrayList<Removal>();
        int remaining = required;
        for (int slot = 0; slot < amounts.length && remaining > 0; slot++) {
            if (!matchesSlot.test(slot) || amounts[slot] <= 0) continue;
            int take = Math.min(amounts[slot], remaining);
            removals.add(new Removal(slot, take));
            remaining -= take;
        }
        return remaining == 0 ? List.copyOf(removals) : List.of();
    }

    public record Removal(int slot, int amount) {}
}

