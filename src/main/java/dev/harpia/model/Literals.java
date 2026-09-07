package dev.harpia.model;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The canonical form of every Harpia literal.
 *
 * <p>One place decides whether a literal belongs to a type, so a default in {@code ## Data} and a
 * value in a {@code ## Scenario} can never disagree about what {@code 2024-01-01} means.
 */
public final class Literals {

    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)");
    private static final Pattern STRING = Pattern.compile(
            "\"(?:[^\"\\\\]|\\\\[\"\\\\/bfnrt]|\\\\u[0-9a-fA-F]{4})*\"");
    private static final Pattern UUID_VALUE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private Literals() {
    }

    public static boolean matches(TypeRef type, String literal) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(literal, "literal");
        try {
            return switch (type) {
                case STRING, TEXT, EMAIL -> STRING.matcher(literal).matches();
                case INT -> INTEGER.matcher(literal).matches() && parseInt(literal);
                case LONG -> INTEGER.matcher(literal).matches() && parseLong(literal);
                case DECIMAL -> DECIMAL.matcher(literal).matches() && parseDecimal(literal);
                case BOOLEAN -> literal.equals("true") || literal.equals("false");
                case UUID -> UUID_VALUE.matcher(literal).matches();
                case DATE -> parseDate(literal);
                case DATE_TIME -> literal.endsWith("Z") && parseInstant(literal);
            };
        } catch (DateTimeException | NumberFormatException ignored) {
            return false;
        }
    }

    /** Accepts a literal written for {@code syntax}, the surface name of a type. */
    public static boolean matches(String syntax, String literal) {
        try {
            return matches(TypeRef.fromSyntax(syntax), literal);
        } catch (IllegalArgumentException unknownType) {
            return false;
        }
    }

    private static boolean parseInt(String value) {
        Integer.parseInt(value);
        return true;
    }

    private static boolean parseLong(String value) {
        Long.parseLong(value);
        return true;
    }

    private static boolean parseDecimal(String value) {
        new BigDecimal(value);
        return true;
    }

    private static boolean parseDate(String value) {
        LocalDate.parse(value);
        return true;
    }

    private static boolean parseInstant(String value) {
        Instant.parse(value);
        return true;
    }
}
