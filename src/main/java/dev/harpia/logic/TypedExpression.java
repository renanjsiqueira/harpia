package dev.harpia.logic;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/**
 * A fully typed, framework-free expression. Every node carries its resolved {@link LogicType} and
 * its own {@link SourceRef}, so diagnostics, generation and future targets never re-parse text.
 *
 * <p>An expression is never stored as a string. {@code total * discount} is a {@link Binary}, not
 * the characters {@code "total * discount"}.
 */
public sealed interface TypedExpression {

    LogicType type();

    SourceRef where();

    /**
     * A literal in its canonical Harpia source form. {@code Decimal} keeps its exact digits so a
     * target can emit an exact decimal value instead of a binary floating point approximation.
     */
    record Literal(LogicType type, String source, SourceRef where) implements TypedExpression {
        public Literal {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(where, "where");
        }
    }

    /** A reference to a parameter or to a local variable of the enclosing Logic. */
    record Variable(String name, LogicType type, SourceRef where) implements TypedExpression {
        public Variable {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    record Binary(
            BinaryOperator operator,
            TypedExpression left,
            TypedExpression right,
            LogicType type,
            SourceRef where) implements TypedExpression {
        public Binary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    record Unary(
            UnaryOperator operator,
            TypedExpression operand,
            LogicType type,
            SourceRef where) implements TypedExpression {
        public Unary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    /**
     * A call to another pure Logic. Arguments are already ordered by the callee's parameters, and
     * {@code parameterTypes} keeps the declared types so a target can widen at the call boundary
     * without looking the callee up again.
     */
    record LogicCall(
            String logicName,
            List<TypedExpression> arguments,
            List<LogicType> parameterTypes,
            LogicType type,
            SourceRef where) implements TypedExpression {
        public LogicCall {
            Objects.requireNonNull(logicName, "logicName");
            arguments = List.copyOf(arguments);
            parameterTypes = List.copyOf(parameterTypes);
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
            if (arguments.size() != parameterTypes.size()) {
                throw new IllegalArgumentException(
                        "every argument of " + logicName + " needs its declared parameter type");
            }
        }
    }

    /** A call to a built-in function of the closed registry. */
    record BuiltinCall(
            BuiltinFunction function,
            List<TypedExpression> arguments,
            LogicType type,
            SourceRef where) implements TypedExpression {
        public BuiltinCall {
            Objects.requireNonNull(function, "function");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }
}
