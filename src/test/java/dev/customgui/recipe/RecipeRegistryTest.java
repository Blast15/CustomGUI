package dev.customgui.recipe;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeRegistryTest {
    @Test void lookupIsByIdAndDuplicatesFail() {
        var recipe = new Recipe("test", "g", "c", true, List.of(), List.of(new ResultSpec("message", Map.of())));
        assertSame(recipe, new RecipeRegistry(List.of(recipe)).find("test").orElseThrow());
        assertThrows(IllegalStateException.class, () -> new RecipeRegistry(List.of(recipe, recipe)));
    }
}
