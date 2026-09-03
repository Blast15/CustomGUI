package dev.customgui.gui;

import java.util.List;

public final class Paginator {
    private Paginator() {}
    public static <T> List<T> page(List<T> values, int page, int pageSize) {
        if (page < 0 || pageSize < 1) throw new IllegalArgumentException("invalid page/pageSize");
        int start = (int) Math.min((long) page * pageSize, values.size());
        return List.copyOf(values.subList(start, Math.min(start + pageSize, values.size())));
    }
}
