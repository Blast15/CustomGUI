package dev.customgui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class MenuActionTest {
    @Test void parsesBracketAndCompactSyntax() {
        assertEquals(new MenuAction(MenuAction.Type.OPEN_MENU, "forge 2"), MenuAction.parse("[openguimenu] forge 2"));
        assertEquals(new MenuAction(MenuAction.Type.RECIPE, "ruby all"), MenuAction.parse("recipe:ruby all"));
        assertEquals(MenuAction.Type.NEXT_PAGE, MenuAction.parse("[next-page]").type());
    }

    @Test void rejectsUnknownEmptyAndMultilineCommands() {
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("[teleport] spawn"));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("menu:"));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("[console] say ok\nstop"));
    }
}
