package dev.customgui.recipe;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeValidatorTest {
    @Test void rejectsUnknownHandlersAndInvalidChance() {
        assertThrows(IllegalArgumentException.class, () -> RecipeValidator.validate(recipe(new RequirementSpec("invented", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> RecipeValidator.validate(recipe(new RequirementSpec("chance", Map.of("chance", 1.1)))));
    }
    private Recipe recipe(RequirementSpec requirement) {
        return new Recipe("valid", "g", "c", true, List.of(requirement),
            List.of(new ResultSpec("give-item", Map.of("provider", "vanilla", "material", "STONE"))));
    }
}
