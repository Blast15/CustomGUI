package dev.customgui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaginatorTest {
    @Test void pagesWithoutOverflow() {
        assertEquals(List.of(3, 4), Paginator.page(List.of(1, 2, 3, 4, 5), 1, 2));
        assertEquals(List.of(), Paginator.page(List.of(1), 5, 2));
        assertEquals(List.of(), Paginator.page(List.of(1), Integer.MAX_VALUE, 54));
    }
}
