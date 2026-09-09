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
