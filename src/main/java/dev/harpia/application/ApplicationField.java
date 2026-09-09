package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

/** Application-level field with its stable relational name. It names no target type. */
public record ApplicationField(
        String name,
        String columnName,
        ApplicationFieldType type,
        boolean required,
        boolean unique,
        boolean generated,
        Optional<String> defaultValue,
        SourceRef where) {

    /**
     * The scalar this field is.
     *
     * <p>Most of the compiler deals with fields whose type is a scalar by construction — a default
     * literal, a generated id, a sample value. Asking here, and failing loudly, is better than
     * every such site carrying a branch for a case its caller already ruled out.
     */
    public ApplicationScalarType scalarType() {
        return type.scalarKind().orElseThrow(() -> new IllegalStateException(
                "field '" + name + "' is " + type.syntax() + ", not a scalar"));
    }

    /** The name of the declared type this field holds, whichever kind it is. */
    public Optional<String> declaredType() {
        return type.scalarKind().isPresent() ? Optional.empty() : Optional.of(type.syntax());
    }

    /** The declared enum this field holds, when it holds one. */
    public Optional<String> enumTypeName() {
        return type instanceof ApplicationFieldType.EnumType declared
                ? Optional.of(declared.name())
                : Optional.empty();
    }

    /** The declared value this field holds, when it holds one. */
    public Optional<ApplicationFieldType.ValueType> valueType() {
        return type instanceof ApplicationFieldType.ValueType declared
                ? Optional.of(declared)
                : Optional.empty();
    }

    /** The values of the declared enum this field holds, in declaration order. */
    public java.util.List<String> enumValues() {
        return type instanceof ApplicationFieldType.EnumType declared
                ? declared.values()
                : java.util.List.of();
    }

    public ApplicationField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(columnName, "columnName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(where, "where");
    }
}
