package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

public record FieldModel(
        String name,
        TypeRef type,
        boolean required,
        boolean unique,
        boolean generated,
        Optional<Literal> defaultValue,
        SourceRef where) {
    public FieldModel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(where, "where");
    }
}
