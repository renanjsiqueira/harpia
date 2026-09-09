package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The untyped syntax tree of a Logic declaration. It exists separately from the typed IR because a
 * Logic may call another Logic declared in a file that has not been parsed yet: declaration and
 * resolution are two passes.
 */
public final class LogicAst {

    private LogicAst() {
    }

    public record Parameter(String name, String type, SourceRef where) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    /**
     * The name of a contract the user implements outside the generated tree.
     *
     * <p>This is the boundary that keeps the grammar small: an algorithm that is specific,
     * recursive or low level leaves the language without leaving the product.
     */
    public record CustomImplementation(String contract, SourceRef where) {
        public CustomImplementation {
            Objects.requireNonNull(contract, "contract");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Declaration(
            String name,
            List<Parameter> parameters,
            String returnType,
            SourceRef returnTypeWhere,
            List<Statement> body,
            Optional<CustomImplementation> custom,
            SourceRef where) implements DeclarationAst {
        public Declaration {
            Objects.requireNonNull(name, "name");
            parameters = List.copyOf(parameters);
            Objects.requireNonNull(returnType, "returnType");
            Objects.requireNonNull(returnTypeWhere, "returnTypeWhere");
            body = List.copyOf(body);
            Objects.requireNonNull(custom, "custom");
            Objects.requireNonNull(where, "where");
            if (body.isEmpty() == custom.isEmpty()) {
                throw new IllegalArgumentException(
                        "logic " + name + " needs exactly one of a body or a custom contract");
            }
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.LOGIC;
        }

        @Override
        public String declaredName() {
            return name;
        }
    }

    /** One {@code name: literal} binding of a scenario. */
    public record Binding(String name, String literal, SourceRef where) {
        public Binding {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(literal, "literal");
            Objects.requireNonNull(where, "where");
        }
    }

    /** {@code ## Scenario Title}: the values a computation is given and the result it must produce. */
    public record Scenario(
            String title,
            String computation,
            SourceRef computationWhere,
            List<Binding> given,
            Binding expected,
            SourceRef where) implements DeclarationAst {
        public Scenario {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(computation, "computation");
            Objects.requireNonNull(computationWhere, "computationWhere");
            given = List.copyOf(given);
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.SCENARIO;
        }

        @Override
        public String declaredName() {
            return title;
        }
    }

    public sealed interface Statement permits Assignment, Conditional, Return {
        SourceRef where();
    }

    public record Assignment(String name, Expression value, SourceRef where) implements Statement {
        public Assignment {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Conditional(
            Expression condition,
            List<Statement> thenBranch,
            Optional<List<Statement>> elseBranch,
            SourceRef where) implements Statement {
        public Conditional {
            Objects.requireNonNull(condition, "condition");
            thenBranch = List.copyOf(thenBranch);
            Objects.requireNonNull(elseBranch, "elseBranch");
            elseBranch = elseBranch.map(List::copyOf);
            Objects.requireNonNull(where, "where");
        }
    }

    public record Return(Expression value, SourceRef where) implements Statement {
        public Return {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }
    }

    public sealed interface Expression
            permits Literal, Reference, MemberAccess, Binary, Unary, Call, BuiltinCall {
        SourceRef where();
    }

    public enum LiteralKind {
        INTEGER,
        DECIMAL,
        STRING,
        BOOLEAN
    }

    public record Literal(LiteralKind kind, String source, SourceRef where) implements Expression {
        public Literal {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Reference(String name, SourceRef where) implements Expression {
        public Reference {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }

    /** {@code customer.vip}. Accepted by the grammar and rejected until nominal types land. */
    public record MemberAccess(Expression target, String member, SourceRef where)
            implements Expression {
        public MemberAccess {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Binary(String operator, Expression left, Expression right, SourceRef where)
            implements Expression {
        public Binary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Unary(String operator, Expression operand, SourceRef where)
            implements Expression {
        public Unary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(where, "where");
        }
    }

    public record NamedArgument(String name, Expression value, SourceRef where) {
        public NamedArgument {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }
    }

    /** A call to a named Logic. Arguments are always named. */
    public record Call(String name, List<NamedArgument> arguments, SourceRef where)
            implements Expression {
        public Call {
            Objects.requireNonNull(name, "name");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(where, "where");
        }
    }

    /** A call to a built-in function. Arguments are positional. */
    public record BuiltinCall(String name, List<Expression> arguments, SourceRef where)
            implements Expression {
        public BuiltinCall {
            Objects.requireNonNull(name, "name");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(where, "where");
        }
    }
}
