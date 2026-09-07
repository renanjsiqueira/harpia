package dev.harpia.target.javaspring;

import dev.harpia.application.ApplicationScalarType;
import java.util.Objects;
import java.util.Optional;

/**
 * How this target realises the PostgreSQL provider: column types, and the JPA column attributes
 * that must agree with them so {@code ddl-auto: validate} passes.
 *
 * <p>PostgreSQL itself is cross-target — a future target would map the same Harpia types to the
 * same columns. When a second target needs this table, it moves to a shared persistence layer.
 */
public final class PostgresTypes {

    private PostgresTypes() {
    }

    public static String column(ApplicationScalarType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case STRING -> "varchar(255)";
            case TEXT -> "text";
            case INT -> "integer";
            case LONG -> "bigint";
            case DECIMAL -> "numeric(19, 2)";
            case BOOLEAN -> "boolean";
            case UUID -> "uuid";
            case EMAIL -> "varchar(320)";
            case DATE -> "date";
            case DATE_TIME -> "timestamp with time zone";
        };
    }

    /** Extra {@code @Column} attributes that keep the entity and the migration in agreement. */
    public static Optional<String> columnAttributes(ApplicationScalarType type) {
        return switch (type) {
            case STRING -> Optional.of("length = 255");
            case EMAIL -> Optional.of("length = 320");
            case TEXT -> Optional.of("columnDefinition = \"text\"");
            case DECIMAL -> Optional.of("precision = 19, scale = 2");
            default -> Optional.empty();
        };
    }
}
