package dev.customgui.recipe;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ItemSpecTest {
    @Test void rejectsOverflowInsteadOfTruncating() {
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.from(Map.of(
            "provider", "vanilla", "material", "DIAMOND", "amount", 4_294_967_297L)));
    }
}
