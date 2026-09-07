package dev.harpia.target.javaspring.mapping;

import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationScalarType;
import java.util.Objects;
import java.util.Optional;

/**
 * Values a generated test uses to stand in for real data.
 *
 * <p>They are derived from the field, never random: a test that changes between two runs cannot
 * tell a regression from noise. Text carries the field name so a failure message says which field
 * it was about, and an {@code Email} stays a valid address so the generated validation does not
 * reject the happy path by accident.
 */
public final class JavaSampleValues {

    private static final String UUID_VALUE = "00000000-0000-0000-0000-000000000001";
    private static final String DATE_VALUE = "2024-01-01";
    private static final String DATE_TIME_VALUE = "2024-01-01T00:00:00Z";

    private JavaSampleValues() {
    }

    /** The value as a Java expression. */
    public static String java(ApplicationField field) {
        Objects.requireNonNull(field, "field");
        return switch (field.type()) {
            case STRING, TEXT -> "\"" + text(field) + "\"";
            case EMAIL -> "\"" + text(field) + "\"";
            case INT -> "1";
            case LONG -> "1L";
            case DECIMAL -> "new BigDecimal(\"1.00\")";
            case BOOLEAN -> "true";
            case UUID -> "UUID.fromString(\"" + UUID_VALUE + "\")";
            case DATE -> "LocalDate.parse(\"" + DATE_VALUE + "\")";
            case DATE_TIME -> "OffsetDateTime.parse(\"" + DATE_TIME_VALUE + "\")";
        };
    }

    /** The same value as a JSON literal, for a request body. */
    public static String json(ApplicationField field) {
        Objects.requireNonNull(field, "field");
        return switch (field.type()) {
            case STRING, TEXT, EMAIL -> "\\\"" + text(field) + "\\\"";
            case INT, LONG -> "1";
            case DECIMAL -> "1.00";
            case BOOLEAN -> "true";
            case UUID -> "\\\"" + UUID_VALUE + "\\\"";
            case DATE -> "\\\"" + DATE_VALUE + "\\\"";
            case DATE_TIME -> "\\\"" + DATE_TIME_VALUE + "\\\"";
        };
    }

    /** The import a Java sample value needs, when it needs one. */
    public static Optional<String> requiredImport(ApplicationScalarType type) {
        return switch (type) {
            case DECIMAL -> Optional.of("java.math.BigDecimal");
            case UUID -> Optional.of("java.util.UUID");
            case DATE -> Optional.of("java.time.LocalDate");
            case DATE_TIME -> Optional.of("java.time.OffsetDateTime");
            default -> Optional.empty();
        };
    }

    private static String text(ApplicationField field) {
        return field.type() == ApplicationScalarType.EMAIL
                ? field.name() + "@example.com"
                : field.name();
    }
}
