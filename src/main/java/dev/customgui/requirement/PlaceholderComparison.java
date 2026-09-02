package dev.customgui.requirement;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PlaceholderComparison {
    private PlaceholderComparison() {}

    public static boolean test(String actual, String expected, String operator, String type) {
        if (actual == null || expected == null) return false;
        operator = operator.toLowerCase(Locale.ROOT);
        type = type == null ? "string" : type.toLowerCase(Locale.ROOT);
        if (type.equals("integer")) return compare(new BigDecimal(parseLong(actual)), new BigDecimal(parseLong(expected)), operator);
        if (type.equals("decimal")) return compare(decimal(actual), decimal(expected), operator);
        if (type.equals("boolean")) {
            boolean left = bool(actual), right = bool(expected);
            return operator.equals("==") ? left == right : operator.equals("!=") && left != right;
        }
        return switch (operator) {
            case "==" -> actual.equals(expected);
            case "!=" -> !actual.equals(expected);
            case "contains" -> actual.contains(expected);
            case "not-contains" -> !actual.contains(expected);
            case "starts-with" -> actual.startsWith(expected);
            case "ends-with" -> actual.endsWith(expected);
            case "matches" -> safeRegex(expected).matcher(actual).matches();
            default -> false;
        };
    }

    private static boolean compare(BigDecimal left, BigDecimal right, String operator) {
        int compared = left.compareTo(right);
        return switch (operator) { case "==" -> compared == 0; case "!=" -> compared != 0; case ">" -> compared > 0;
            case ">=" -> compared >= 0; case "<" -> compared < 0; case "<=" -> compared <= 0; default -> false; };
    }
    private static long parseLong(String value) { try { return Long.parseLong(value.trim()); } catch (NumberFormatException ex) { throw new IllegalArgumentException("not an integer"); } }
    private static BigDecimal decimal(String value) { try { return new BigDecimal(value.trim()); } catch (NumberFormatException ex) { throw new IllegalArgumentException("not a decimal"); } }
    private static boolean bool(String value) { if (value.equalsIgnoreCase("true")) return true; if (value.equalsIgnoreCase("false")) return false; throw new IllegalArgumentException("not a boolean"); }
    private static Pattern safeRegex(String regex) {
        if (regex.length() > 128 || regex.matches(".*(?:\\([^)]*[+*{][^)]*\\)[+*{]|\\\\[1-9]|\\(\\?[=!<]).*"))
            throw new IllegalArgumentException("unsafe regex");
        try { return Pattern.compile(regex); } catch (PatternSyntaxException ex) { throw new IllegalArgumentException("invalid regex", ex); }
    }
}
