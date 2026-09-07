package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

/** Application-level field with its stable relational name. It names no target type. */
public record ApplicationField(
        String name,
        String columnName,
        ApplicationScalarType type,
        boolean required,
        boolean unique,
        boolean generated,
        Optional<String> defaultValue,
        SourceRef where) {

    public ApplicationField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(columnName, "columnName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(where, "where");
    }
}
