package dev.customgui.gui;

import java.util.Locale;

public record MenuAction(Type type, String value) {
    public enum Type { CLOSE, REFRESH, NEXT_PAGE, PREVIOUS_PAGE, OPEN_MENU, RECIPE, MESSAGE, PLAYER_COMMAND, CONSOLE_COMMAND }

    public static MenuAction parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("empty menu action");
        String text = input.trim(), head, value;
        if (text.startsWith("[")) {
            int end = text.indexOf(']');
            if (end < 1) throw new IllegalArgumentException("invalid menu action: " + input);
            head = text.substring(1, end).trim();
            value = text.substring(end + 1).trim();
        } else {
            int separator = text.indexOf(':');
            head = separator < 0 ? text : text.substring(0, separator);
            value = separator < 0 ? "" : text.substring(separator + 1).trim();
        }
        Type type = switch (head.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "close" -> Type.CLOSE;
            case "refresh" -> Type.REFRESH;
            case "next-page", "next" -> Type.NEXT_PAGE;
            case "previous-page", "previous", "back-page" -> Type.PREVIOUS_PAGE;
            case "open-menu", "openguimenu", "menu" -> Type.OPEN_MENU;
            case "recipe", "exchange" -> Type.RECIPE;
            case "message" -> Type.MESSAGE;
            case "player" -> Type.PLAYER_COMMAND;
            case "console" -> Type.CONSOLE_COMMAND;
            default -> throw new IllegalArgumentException("unknown menu action: " + head);
        };
        if (switch (type) { case OPEN_MENU, RECIPE, MESSAGE, PLAYER_COMMAND, CONSOLE_COMMAND -> value.isBlank(); default -> false; })
            throw new IllegalArgumentException(type + " action requires a value");
        if ((type == Type.PLAYER_COMMAND || type == Type.CONSOLE_COMMAND) && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0))
            throw new IllegalArgumentException("commands cannot contain line breaks");
        return new MenuAction(type, value);
    }
}
