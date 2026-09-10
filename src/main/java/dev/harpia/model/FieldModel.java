package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

public record FieldModel(
        String name,
        FieldType type,
        boolean required,
        boolean unique,
        boolean generated,
        boolean indexed,
        Optional<Literal> defaultValue,
        SourceRef where) {
    public FieldModel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(where, "where");
    }
}
