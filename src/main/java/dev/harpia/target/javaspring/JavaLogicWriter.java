package dev.harpia.target.javaspring;

import dev.harpia.application.ApplicationLogic;
import dev.harpia.logic.BinaryOperator;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedExpression;
import dev.harpia.logic.TypedStatement;
import dev.harpia.model.TypeRef;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Writes the Java body of a pure computation by walking the typed statement tree.
 *
 * <p>A logic-less template cannot express nested conditionals, so the body is written here while
 * the class wrapper stays a template. {@code Decimal} always becomes {@code BigDecimal}: floating
 * point never appears, decimal literals use the exact {@code String} constructor and comparison
 * uses {@code compareTo} instead of {@code equals}, which would compare scale.
 */
public final class JavaLogicWriter {

    private static final String INDENT = "    ";

    private final StringBuilder body = new StringBuilder();
    private final TreeSet<String> imports = new TreeSet<>();
    private final String methodName;

    private JavaLogicWriter(String methodName) {
        this.methodName = methodName;
    }

    public static Result write(ApplicationLogic logic, String methodName) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(methodName, "methodName");
        JavaLogicWriter writer = new JavaLogicWriter(methodName);
        logic.parameters().forEach(parameter -> writer.importFor(parameter.type()));
        writer.importFor(logic.returnType());
        writer.statements(logic.body(), logic.returnType(), 2);
        return new Result(writer.body.toString(), List.copyOf(writer.imports));
    }

    private void statements(List<TypedStatement> statements, LogicType returnType, int depth) {
        for (TypedStatement statement : statements) {
            statement(statement, returnType, depth);
        }
    }

    private void statement(TypedStatement statement, LogicType returnType, int depth) {
        if (statement instanceof TypedStatement.Assignment assignment) {
            importFor(assignment.type());
            line(depth, "final " + JavaTypeMapper.map(assignment.type()).simpleName() + " "
                    + assignment.name() + " = "
                    + render(assignment.value(), assignment.type()).code() + ";");
            return;
        }
        if (statement instanceof TypedStatement.Return returned) {
            line(depth, "return " + render(returned.value(), returnType).code() + ";");
            return;
        }
        TypedStatement.Conditional conditional = (TypedStatement.Conditional) statement;
        line(depth, "if (" + render(conditional.condition(), LogicType.BOOLEAN).code() + ") {");
        statements(conditional.thenBranch(), returnType, depth + 1);
        if (conditional.elseBranch().isPresent()) {
            line(depth, "} else {");
            statements(conditional.elseBranch().orElseThrow(), returnType, depth + 1);
        }
        line(depth, "}");
    }

    private void line(int depth, String text) {
        body.append(INDENT.repeat(depth)).append(text).append('\n');
    }

    /** Renders {@code expression} so that its value has type {@code target}. */
    private Java render(TypedExpression expression, LogicType target) {
        Java value = raw(expression);
        return widen(expression, value, expression.type(), target);
    }

    private Java widen(TypedExpression source, Java value, LogicType from, LogicType target) {
        if (from.equals(target) || !from.isNumeric() || !target.isNumeric()) {
            return value;
        }
        if (isDecimal(target)) {
            importFor(target);
            if (source instanceof TypedExpression.Literal literal) {
                return new Java(decimal(literal.source()), Precedence.PRIMARY);
            }
            return new Java("BigDecimal.valueOf(" + value.code() + ")", Precedence.PRIMARY);
        }
        if (source instanceof TypedExpression.Literal literal) {
            return new Java(literal.source() + "L", Precedence.PRIMARY);
        }
        return new Java("Long.valueOf(" + value.code() + ")", Precedence.PRIMARY);
    }

    private Java raw(TypedExpression expression) {
        if (expression instanceof TypedExpression.Literal literal) {
            return literal(literal);
        }
        if (expression instanceof TypedExpression.Variable variable) {
            return new Java(variable.name(), Precedence.PRIMARY);
        }
        if (expression instanceof TypedExpression.Unary unary) {
            return unary(unary);
        }
        if (expression instanceof TypedExpression.Binary binary) {
            return binary(binary);
        }
        if (expression instanceof TypedExpression.LogicCall call) {
            List<String> arguments = new ArrayList<>();
            for (int index = 0; index < call.arguments().size(); index++) {
                arguments.add(render(
                        call.arguments().get(index), call.parameterTypes().get(index)).code());
            }
            return new Java(
                    call.logicName() + "." + methodName + "("
                            + String.join(", ", arguments) + ")",
                    Precedence.PRIMARY);
        }
        TypedExpression.BuiltinCall call = (TypedExpression.BuiltinCall) expression;
        return builtin(call);
    }

    private Java literal(TypedExpression.Literal literal) {
        if (!(literal.type() instanceof LogicType.Scalar scalar)) {
            throw new IllegalStateException("no Java literal for " + literal.type().display());
        }
        return switch (scalar.kind()) {
            case DECIMAL -> {
                importFor(literal.type());
                yield new Java(decimal(literal.source()), Precedence.PRIMARY);
            }
            case LONG -> new Java(literal.source() + "L", Precedence.PRIMARY);
            case STRING, TEXT, EMAIL -> new Java(javaString(literal.source()), Precedence.PRIMARY);
            default -> new Java(literal.source(), Precedence.PRIMARY);
        };
    }

    private Java unary(TypedExpression.Unary unary) {
        Java operand = render(unary.operand(), unary.type());
        if (unary.operator() == dev.harpia.logic.UnaryOperator.NOT) {
            return new Java("!" + parenthesize(operand, Precedence.UNARY), Precedence.UNARY);
        }
        if (isDecimal(unary.type())) {
            return new Java(
                    parenthesize(operand, Precedence.PRIMARY) + ".negate()", Precedence.PRIMARY);
        }
        return new Java("-" + parenthesize(operand, Precedence.UNARY), Precedence.UNARY);
    }

    private Java binary(TypedExpression.Binary binary) {
        BinaryOperator operator = binary.operator();
        if (operator.isLogical()) {
            int precedence = operator == BinaryOperator.AND ? Precedence.AND : Precedence.OR;
            return new Java(
                    parenthesize(render(binary.left(), LogicType.BOOLEAN), precedence)
                            + " " + (operator == BinaryOperator.AND ? "&&" : "||") + " "
                            + parenthesize(render(binary.right(), LogicType.BOOLEAN), precedence - 1),
                    precedence);
        }

        LogicType operands = operator.isArithmetic()
                ? binary.type()
                : LogicType.unify(binary.left().type(), binary.right().type()).orElseThrow();
        if (operator == BinaryOperator.DIVIDE) {
            operands = LogicType.DECIMAL;
        }
        Java left = render(binary.left(), operands);
        Java right = render(binary.right(), operands);

        if (operator.isArithmetic()) {
            return isDecimal(operands)
                    ? decimalArithmetic(operator, left, right)
                    : new Java(
                            infix(left, right, operator.symbol(), precedenceOf(operator)),
                            precedenceOf(operator));
        }
        if (isDecimal(operands)) {
            String comparison = parenthesize(left, Precedence.PRIMARY)
                    + ".compareTo(" + right.code() + ") " + operator.symbol() + " 0";
            return new Java(comparison, operator.isEquality()
                    ? Precedence.EQUALITY
                    : Precedence.RELATIONAL);
        }
        if (operator.isEquality()) {
            imports.add("java.util.Objects");
            String equals = "Objects.equals(" + left.code() + ", " + right.code() + ")";
            return operator == BinaryOperator.EQUAL
                    ? new Java(equals, Precedence.PRIMARY)
                    : new Java("!" + equals, Precedence.UNARY);
        }
        return new Java(
                infix(left, right, operator.symbol(), Precedence.RELATIONAL),
                Precedence.RELATIONAL);
    }

    private Java decimalArithmetic(BinaryOperator operator, Java left, Java right) {
        String method = switch (operator) {
            case ADD -> "add";
            case SUBTRACT -> "subtract";
            case MULTIPLY -> "multiply";
            case DIVIDE -> "divide";
            default -> throw new IllegalStateException("not arithmetic: " + operator);
        };
        String arguments = right.code();
        if (operator == BinaryOperator.DIVIDE) {
            imports.add("java.math.MathContext");
            arguments = arguments + ", MathContext.DECIMAL128";
        }
        return new Java(
                parenthesize(left, Precedence.PRIMARY) + "." + method + "(" + arguments + ")",
                Precedence.PRIMARY);
    }

    private Java builtin(TypedExpression.BuiltinCall call) {
        List<String> arguments = call.arguments().stream()
                .map(argument -> render(argument, call.type()).code())
                .toList();
        if (isDecimal(call.type())) {
            return new Java(
                    arguments.getFirst() + "." + call.function().name()
                            + "(" + arguments.get(1) + ")",
                    Precedence.PRIMARY);
        }
        return new Java(
                "Math." + call.function().name() + "(" + String.join(", ", arguments) + ")",
                Precedence.PRIMARY);
    }

    private static String infix(Java left, Java right, String operator, int precedence) {
        return parenthesize(left, precedence) + " " + operator + " "
                + parenthesize(right, precedence - 1);
    }

    private static String parenthesize(Java value, int maximum) {
        return value.precedence() <= maximum ? value.code() : "(" + value.code() + ")";
    }

    private static int precedenceOf(BinaryOperator operator) {
        return switch (operator) {
            case MULTIPLY, DIVIDE -> Precedence.MULTIPLICATIVE;
            case ADD, SUBTRACT -> Precedence.ADDITIVE;
            default -> throw new IllegalStateException("not arithmetic: " + operator);
        };
    }

    private static boolean isDecimal(LogicType type) {
        return type instanceof LogicType.Scalar scalar && scalar.kind() == TypeRef.DECIMAL;
    }

    private static String decimal(String source) {
        return "new BigDecimal(\"" + source + "\")";
    }

    /** JSON allows {@code \\/}; Java does not. Every other Harpia escape is already valid Java. */
    private static String javaString(String source) {
        return source.replace("\\/", "/");
    }

    private void importFor(LogicType type) {
        String canonicalName = JavaTypeMapper.map(type).canonicalName();
        if (canonicalName.contains(".") && !canonicalName.startsWith("java.lang.")) {
            imports.add(canonicalName);
        }
    }

    /** A rendered Java expression and the precedence of its outermost operator. */
    private record Java(String code, int precedence) {
    }

    private static final class Precedence {
        private static final int PRIMARY = 0;
        private static final int UNARY = 1;
        private static final int MULTIPLICATIVE = 2;
        private static final int ADDITIVE = 3;
        private static final int RELATIONAL = 4;
        private static final int EQUALITY = 5;
        private static final int AND = 6;
        private static final int OR = 7;

        private Precedence() {
        }
    }

    public record Result(String body, List<String> imports) {
        public Result {
            Objects.requireNonNull(body, "body");
            imports = List.copyOf(imports);
        }

        /** Statement lines relative to a method body; nested indentation is preserved. */
        public List<String> statements() {
            return body.lines().map(line -> line.startsWith("        ")
                    ? line.substring(8)
                    : line).toList();
        }
    }
}
