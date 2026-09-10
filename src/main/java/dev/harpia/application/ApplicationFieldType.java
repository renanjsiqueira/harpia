package dev.harpia.application;

import java.util.Objects;
import java.util.Optional;

/**
 * The type of an application field.
 *
 * <p>It mirrors the business algebra rather than flattening it: a target that maps scalars must
 * still be told, explicitly, what to do with a type the project declared.
 */
public sealed interface ApplicationFieldType {

    /** The name as written in the specification. */
    String syntax();

    record Scalar(ApplicationScalarType kind) implements ApplicationFieldType {
        public Scalar {
            Objects.requireNonNull(kind, "kind");
        }

        @Override
        public String syntax() {
            return kind.name();
        }
    }

    /** A closed set of values the project declared. */
    record EnumType(String name, java.util.List<String> values) implements ApplicationFieldType {
        public EnumType {
            Objects.requireNonNull(name, "name");
            values = java.util.List.copyOf(values);
        }

        @Override
        public String syntax() {
            return name;
        }
    }

    /** A named group of fields, compared by what it holds. */
    record ValueType(String name, java.util.List<ApplicationField> components)
            implements ApplicationFieldType {
        public ValueType {
            Objects.requireNonNull(name, "name");
            components = java.util.List.copyOf(components);
        }

        @Override
        public String syntax() {
            return name;
        }
    }

    static ApplicationFieldType value(String name, java.util.List<ApplicationField> components) {
        return new ValueType(name, components);
    }

    /** A homogeneous collection of one element type. */
    record Container(ApplicationFieldType element) implements ApplicationFieldType {
        public Container {
            Objects.requireNonNull(element, "element");
        }

        @Override
        public String syntax() {
            return "List<" + element.syntax() + ">";
        }
    }

    /** A value that may be absent. */
    record Optionality(ApplicationFieldType element) implements ApplicationFieldType {
        public Optionality {
            Objects.requireNonNull(element, "element");
        }

        @Override
        public String syntax() {
            return "Optional<" + element.syntax() + ">";
        }
    }

    /** A pointer to another entity's identity, carrying which entity and which id type. */
    record Reference(String entity, ApplicationScalarType idType)
            implements ApplicationFieldType {
        public Reference {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(idType, "idType");
        }

        @Override
        public String syntax() {
            return "Reference<" + entity + ">";
        }
    }

    /**
     * An independently-lived entity association.
     *
     * <p>The target identity type is retained for relational schema generation. The field itself
     * holds the target entity, unlike {@link Reference}, which holds only that identity.
     */
    record Relationship(
            String entity,
            ApplicationScalarType idType,
            RelationshipLoading loading,
            RelationshipLifecycle lifecycle)
            implements ApplicationFieldType {
        public Relationship {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(idType, "idType");
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
        INDEPENDENT
    }

    static ApplicationFieldType reference(String entity, ApplicationScalarType idType) {
        return new Reference(entity, idType);
    }

    static ApplicationFieldType relationship(
            String entity,
            ApplicationScalarType idType,
            RelationshipLoading loading,
            RelationshipLifecycle lifecycle) {
        return new Relationship(entity, idType, loading, lifecycle);
    }

    static ApplicationFieldType optional(ApplicationFieldType element) {
        return new Optionality(element);
    }

    static ApplicationFieldType list(ApplicationFieldType element) {
        return new Container(element);
    }

    static ApplicationFieldType scalar(ApplicationScalarType kind) {
        return new Scalar(kind);
    }

    static ApplicationFieldType enumeration(String name, java.util.List<String> values) {
        return new EnumType(name, values);
    }

    /** The scalar this type is, when it is one. */
    default Optional<ApplicationScalarType> scalarKind() {
        return this instanceof Scalar scalar
                ? Optional.of(scalar.kind())
                : Optional.empty();
    }
}
