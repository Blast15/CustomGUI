package dev.customgui.gui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SlotParser {
    private SlotParser() {}

    public static List<Integer> parse(Object input, int inventorySize) {
        var slots = new LinkedHashSet<Integer>();
        if (input instanceof List<?> list) list.forEach(value -> add(value, inventorySize, slots));
        else add(input, inventorySize, slots);
        if (slots.isEmpty()) throw new IllegalArgumentException("slots cannot be empty");
        return List.copyOf(slots);
    }

    private static void add(Object input, int size, Set<Integer> slots) {
        if (input instanceof Number number) { checked(number.intValue(), size, slots); return; }
        if (!(input instanceof String text)) throw new IllegalArgumentException("slot must be an integer or range");
        text = text.trim();
        int dash = text.indexOf('-');
        try {
            if (dash < 0) { checked(Integer.parseInt(text), size, slots); return; }
            int start = Integer.parseInt(text.substring(0, dash).trim());
            int end = Integer.parseInt(text.substring(dash + 1).trim());
            if (start > end) throw new IllegalArgumentException("slot range is reversed: " + text);
            for (int slot = start; slot <= end; slot++) checked(slot, size, slots);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid slot: " + text, ex);
        }
    }

    private static void checked(int slot, int size, Set<Integer> slots) {
        if (slot < 0 || slot >= size) throw new IllegalArgumentException("slot " + slot + " outside inventory size " + size);
        slots.add(slot);
    }
}

