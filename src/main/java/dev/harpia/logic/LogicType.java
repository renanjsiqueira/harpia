package dev.harpia.logic;

import dev.harpia.model.TypeRef;
import java.util.Objects;

/**
 * The Harpia Logic type algebra. It is a sealed algebra instead of a growing enum so that
 * {@code List<T>}, {@code Money} and nominal types can be added by extension, never by migration.
 *
 * <p>No Java or database type names appear here.
 */
public sealed interface LogicType
        permits LogicType.Scalar, LogicType.Nominal, LogicType.Container, LogicType.Optionality {

    /** Human-facing name used by diagnostics. It is always Harpia syntax, never a Java name. */
    String display();

    record Scalar(TypeRef kind) implements LogicType {
        public Scalar {
            Objects.requireNonNull(kind, "kind");
        }

        @Override
        public String display() {
            return kind.syntax();
        }
    }

    /** A project-declared Enum or Value, kept nominal across a typed Flow invocation. */
    record Nominal(String name) implements LogicType {
        public Nominal {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String display() {
            return name;
        }
    }

    /** A homogeneous value collection carried through an application boundary. */
    record Container(LogicType element) implements LogicType {
        public Container {
            Objects.requireNonNull(element, "element");
        }

        @Override
        public String display() {
            return "List<" + element.display() + ">";
        }
    }

    /** Explicit absence in a typed application boundary. */
    record Optionality(LogicType element) implements LogicType {
        public Optionality {
            Objects.requireNonNull(element, "element");
        }

        @Override
        public String display() {
            return "Optional<" + element.display() + ">";
        }
    }

    static LogicType scalar(TypeRef kind) {
        return new Scalar(kind);
    }

    static LogicType of(String syntax) {
        return scalar(TypeRef.fromSyntax(syntax));
    }

    LogicType INT = scalar(TypeRef.INT);
    LogicType LONG = scalar(TypeRef.LONG);
    LogicType DECIMAL = scalar(TypeRef.DECIMAL);
    LogicType BOOLEAN = scalar(TypeRef.BOOLEAN);

    default boolean isNumeric() {
        return this instanceof Scalar scalar
                && (scalar.kind() == TypeRef.INT
                        || scalar.kind() == TypeRef.LONG
                        || scalar.kind() == TypeRef.DECIMAL);
    }

    default boolean isBoolean() {
        return this instanceof Scalar scalar && scalar.kind() == TypeRef.BOOLEAN;
    }

    default boolean isTextual() {
        return this instanceof Scalar scalar
                && (scalar.kind() == TypeRef.STRING
                        || scalar.kind() == TypeRef.TEXT
                        || scalar.kind() == TypeRef.EMAIL);
    }

    /**
     * Numeric rank in the widening tower {@code Int < Long < Decimal}. Returns {@code -1} for
     * every non-numeric type.
     */
    default int numericRank() {
        if (!(this instanceof Scalar scalar)) {
            return -1;
        }
        return switch (scalar.kind()) {
            case INT -> 0;
            case LONG -> 1;
            case DECIMAL -> 2;
            default -> -1;
        };
    }

    /** True when a value of this type may be used where {@code target} is expected. */
    default boolean assignableTo(LogicType target) {
        Objects.requireNonNull(target, "target");
        if (equals(target)) {
            return true;
        }
        if (isNumeric() && target.isNumeric()) {
            return numericRank() <= target.numericRank();
        }
        return isTextual() && target.isTextual();
    }

    /** The type both operands widen to, or empty when the two types do not unify. */
    static java.util.Optional<LogicType> unify(LogicType left, LogicType right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.equals(right)) {
            return java.util.Optional.of(left);
        }
        if (left.isNumeric() && right.isNumeric()) {
            return java.util.Optional.of(
                    left.numericRank() >= right.numericRank() ? left : right);
        }
        if (left.isTextual() && right.isTextual()) {
            return java.util.Optional.of(LogicType.scalar(TypeRef.STRING));
        }
        return java.util.Optional.empty();
    }
}
