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
        boolean indexed,
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
        return present().scalarKind().orElseThrow(() -> new IllegalStateException(
                "field '" + name + "' is " + type.syntax() + ", not a scalar"));
    }

    /**
     * The type this field holds when the value is present.
     *
     * <p>Optionality says whether a value is there, not what it is. A storage column, a sample
     * value and a constraint all care about the second question, so they ask this one.
     */
    public ApplicationFieldType present() {
        return type instanceof ApplicationFieldType.Optionality optional
                ? optional.element()
                : type;
    }

    /**
     * The name of the declared type this field holds.
     *
     * <p>A container is neither a scalar nor a declaration: it is built from an element type, and
     * asking it for a name would produce {@code List<Thing>}, which names nothing.
     */
    public Optional<String> declaredType() {
        ApplicationFieldType held = present();
        return held instanceof ApplicationFieldType.EnumType
                        || held instanceof ApplicationFieldType.ValueType
                ? Optional.of(held.syntax())
                : Optional.empty();
    }

    /** The declared enum this field holds, when it holds one. */
    public Optional<String> enumTypeName() {
        return present() instanceof ApplicationFieldType.EnumType declared
                ? Optional.of(declared.name())
                : Optional.empty();
    }

    /** The declared value this field holds, when it holds one. */
    public Optional<ApplicationFieldType.ValueType> valueType() {
        return present() instanceof ApplicationFieldType.ValueType declared
                ? Optional.of(declared)
                : Optional.empty();
    }

    /** The entity this field points at, when it points at one. */
    public Optional<ApplicationFieldType.Reference> reference() {
        return present() instanceof ApplicationFieldType.Reference declared
                ? Optional.of(declared)
                : Optional.empty();
    }

    /** The entity this field loads, when it is a direct association. */
    public Optional<ApplicationFieldType.Relationship> relationship() {
        return present() instanceof ApplicationFieldType.Relationship declared
                ? Optional.of(declared)
                : Optional.empty();
    }

    /** The entity this collection loads, when its elements are associations. */
    public Optional<ApplicationFieldType.Relationship> relationshipElement() {
        return elementType()
                .filter(ApplicationFieldType.Relationship.class::isInstance)
                .map(ApplicationFieldType.Relationship.class::cast);
    }

    /** Whether this field is either a to-one or to-many entity association. */
    public boolean isRelationship() {
        return relationship().isPresent() || relationshipElement().isPresent();
    }

    /** The type this field may be absent of, when absence is declared. */
    public Optional<ApplicationFieldType> optionalType() {
        return type instanceof ApplicationFieldType.Optionality optional
                ? Optional.of(optional.element())
                : Optional.empty();
    }

    /** The element type this field collects, when it is a collection. */
    public Optional<ApplicationFieldType> elementType() {
        return present() instanceof ApplicationFieldType.Container container
                ? Optional.of(container.element())
                : Optional.empty();
    }

    /** The values of the declared enum this field holds, in declaration order. */
    public java.util.List<String> enumValues() {
        return present() instanceof ApplicationFieldType.EnumType declared
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
