package dev.customgui.transaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class InventorySimulation {
    private InventorySimulation() {}

    public static Optional<ItemStack[]> apply(ItemStack[] source, Map<Integer, Integer> removals, List<ItemStack> outputs) {
        ItemStack[] result = cloneContents(source);
        for (var removal : removals.entrySet()) {
            int slot = removal.getKey(), amount = removal.getValue();
            if (slot < 0 || slot >= result.length || amount < 1) return Optional.empty();
            ItemStack stack = result[slot];
            if (stack == null || stack.getAmount() < amount) return Optional.empty();
            int remaining = stack.getAmount() - amount;
            if (remaining == 0) result[slot] = null; else stack.setAmount(remaining);
        }
        for (ItemStack requested : outputs) {
            if (requested == null || requested.getType().isAir() || requested.getAmount() < 1) return Optional.empty();
            var amounts = new java.util.ArrayList<Integer>();
            var maximums = new java.util.ArrayList<Integer>();
            int emptySlots = 0;
            for (ItemStack existing : result) {
                if (existing == null) { emptySlots++; continue; }
                if (existing.isSimilar(requested)) { amounts.add(existing.getAmount()); maximums.add(existing.getMaxStackSize()); }
            }
            int[] amountArray = amounts.stream().mapToInt(Integer::intValue).toArray();
            int[] maximumArray = maximums.stream().mapToInt(Integer::intValue).toArray();
            if (!CapacityPlanner.canFit(amountArray, maximumArray, emptySlots, requested.getMaxStackSize(), requested.getAmount())) return Optional.empty();
            int remaining = requested.getAmount();
            for (ItemStack existing : result) {
                if (existing == null || !existing.isSimilar(requested)) continue;
                int space = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
                int moved = Math.min(space, remaining);
                existing.setAmount(existing.getAmount() + moved); remaining -= moved;
                if (remaining == 0) break;
            }
            for (int slot = 0; slot < result.length && remaining > 0; slot++) {
                if (result[slot] != null) continue;
                ItemStack inserted = requested.clone();
                int moved = Math.min(inserted.getMaxStackSize(), remaining);
                inserted.setAmount(moved); result[slot] = inserted; remaining -= moved;
            }
            if (remaining > 0) return Optional.empty();
        }
        return Optional.of(result);
    }

    public static ItemStack[] cloneContents(ItemStack[] input) {
        ItemStack[] copy = new ItemStack[input.length];
        for (int slot = 0; slot < input.length; slot++) copy[slot] = input[slot] == null ? null : input[slot].clone();
        return copy;
    }
}
