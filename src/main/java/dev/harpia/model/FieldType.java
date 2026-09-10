package dev.harpia.model;

import java.util.Objects;

/**
 * The type of a declared field.
 *
 * <p>It is a sealed algebra rather than a wider enum because a nominal type is not another scalar:
 * it has a declaration, a name the project chose, and values only it knows. Adding one must not
 * force every scalar mapping in the compiler to grow a case that means nothing to it.
 */
public sealed interface FieldType {

    /** The name as written in a specification. */
    String syntax();

    /** One of the built-in scalars. */
    record Scalar(TypeRef kind) implements FieldType {
        public Scalar {
            Objects.requireNonNull(kind, "kind");
        }

        @Override
        public String syntax() {
            return kind.syntax();
        }
    }

    /** A type the project declared, referenced by the name it was given. */
    record Nominal(
            String name,
            Kind kind,
            java.util.List<String> values,
            java.util.List<FieldModel> components) implements FieldType {
        public Nominal {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            values = java.util.List.copyOf(values);
            components = java.util.List.copyOf(components);
        }

        @Override
        public String syntax() {
            return name;
        }
    }

    /** Which declaration a nominal type came from. */
    enum Kind {
        ENUM,
        VALUE
    }

    /** A homogeneous collection of one element type. */
    record Container(FieldType element) implements FieldType {
        public Container {
            Objects.requireNonNull(element, "element");
        }

        @Override
        public String syntax() {
            return "List<" + element.syntax() + ">";
        }
    }

    /** A value that may be absent, said out loud instead of inferred from a missing modifier. */
    record Optionality(FieldType element) implements FieldType {
        public Optionality {
            Objects.requireNonNull(element, "element");
        }

        @Override
        public String syntax() {
            return "Optional<" + element.syntax() + ">";
        }
    }

    /**
     * A pointer to another entity's identity.
     *
     * <p>It is a type, not a relationship: it holds which entity is meant and which row, and says
     * nothing about loading it, cascading to it or owning its lifetime. Those are `DOM-012`.
     */
    record Reference(String entity) implements FieldType {
        public Reference {
            Objects.requireNonNull(entity, "entity");
        }

        @Override
        public String syntax() {
            return "Reference<" + entity + ">";
        }
    }

    /**
     * An association to another entity.
     *
     * <p>Unlike {@link Reference}, this field holds and loads the entity itself. A bare entity name
     * declares an independent to-one association; wrapping it in {@link Container} declares an
     * independent collection association. Ownership and dependent lifecycle are separate syntax.
     */
    record Relationship(
            String entity,
            RelationshipLoading loading,
            RelationshipLifecycle lifecycle) implements FieldType {
        public Relationship {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(loading, "loading");
            Objects.requireNonNull(lifecycle, "lifecycle");
        }

        @Override
        public String syntax() {
            return entity;
        }
    }

    enum RelationshipLoading {
        LAZY
    }

    enum RelationshipLifecycle {
        INDEPENDENT,
        DEPENDENT
    }

    static FieldType reference(String entity) {
        return new Reference(entity);
    }

    static FieldType relationship(String entity) {
        return new Relationship(
                entity, RelationshipLoading.LAZY, RelationshipLifecycle.INDEPENDENT);
    }

    static FieldType ownedRelationship(String entity) {
        return new Relationship(
                entity, RelationshipLoading.LAZY, RelationshipLifecycle.DEPENDENT);
    }

    static FieldType optional(FieldType element) {
        return new Optionality(element);
    }

    static FieldType list(FieldType element) {
        return new Container(element);
    }

    static FieldType scalar(TypeRef kind) {
        return new Scalar(kind);
    }

    static FieldType enumeration(String name, java.util.List<String> values) {
        return new Nominal(name, Kind.ENUM, values, java.util.List.of());
    }

    static FieldType value(String name, java.util.List<FieldModel> components) {
        return new Nominal(name, Kind.VALUE, java.util.List.of(), components);
    }

    /** The scalar this type is, when it is one. */
    default java.util.Optional<TypeRef> scalarKind() {
        return this instanceof Scalar scalar
                ? java.util.Optional.of(scalar.kind())
                : java.util.Optional.empty();
    }
}
