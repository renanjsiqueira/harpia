package dev.harpia.validate;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.logic.BinaryOperator;
import dev.harpia.logic.BuiltinFunction;
import dev.harpia.logic.BuiltinRegistry;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedExpression;
import dev.harpia.logic.TypedStatement;
import dev.harpia.logic.UnaryOperator;
import dev.harpia.model.LogicModel;
import dev.harpia.model.ScenarioModel;
import dev.harpia.model.TypeRef;
import dev.harpia.parse.LogicAst;
import dev.harpia.parse.SpecAst;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolve pass of Harpia Logic: scope, type inference, type checking, definite return, purity and
 * the acyclic call graph. It produces the typed Business IR; the Java generator never re-derives a
 * type.
 */
public final class LogicAnalyzer {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private LogicAnalyzer() {
    }

    /** Returns the typed Logic IR of the whole project, in declaration order. */
    public static Result analyze(
            List<SpecAst> specifications, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(specifications, "specifications");
        Objects.requireNonNull(diagnostics, "diagnostics");

        LogicSymbols symbols = LogicSymbols.declare(specifications, diagnostics);
        List<LogicModel> models = new ArrayList<>();
        Map<String, Set<String>> callGraph = new LinkedHashMap<>();

        for (SpecAst specification : specifications) {
            for (LogicAst.Declaration declaration : specification.logics()) {
                Optional<LogicSymbols.Signature> signature = symbols.lookup(declaration.name());
                if (signature.isEmpty() || !signature.orElseThrow().where().equals(declaration.where())) {
                    continue;
                }
                Body body = new Body(signature.orElseThrow(), symbols, diagnostics);
                Optional<LogicModel> model = body.analyze(declaration);
                callGraph.put(declaration.name(), body.calls);
                model.ifPresent(models::add);
            }
        }

        reportCycles(callGraph, symbols, diagnostics);
        return new Result(
                List.copyOf(models),
                ScenarioAnalyzer.analyze(specifications, symbols, diagnostics));
    }

    /** Computations and the examples declared for them, resolved against one symbol table. */
    public record Result(List<LogicModel> logics, List<ScenarioModel> scenarios) {
        public Result {
            logics = List.copyOf(logics);
            scenarios = List.copyOf(scenarios);
        }
    }

    private static void reportCycles(
            Map<String, Set<String>> callGraph,
            LogicSymbols symbols,
            DiagnosticCollector diagnostics) {
        Set<String> reported = new LinkedHashSet<>();
        for (String start : callGraph.keySet()) {
            List<String> path = new ArrayList<>();
            if (reachesItself(start, start, callGraph, path, new LinkedHashSet<>())
                    && reported.add(start)) {
                symbols.lookup(start).ifPresent(signature -> diagnostics.error(
                        ErrorCodes.SEMANTIC_LOGIC_RECURSION,
                        "Logic '" + start + "' is recursive through "
                                + String.join(" -> ", path)
                                + "; recursion is not supported, use '### Implementation custom'",
                        signature.where()));
            }
        }
    }

    private static boolean reachesItself(
            String start,
            String current,
            Map<String, Set<String>> callGraph,
            List<String> path,
            Set<String> visited) {
        for (String next : callGraph.getOrDefault(current, Set.of())) {
            path.add(next);
            if (next.equals(start)) {
                return true;
            }
            if (visited.add(next) && reachesItself(start, next, callGraph, path, visited)) {
                return true;
            }
            path.removeLast();
        }
        return false;
    }

    /** Analysis state of a single Logic body. */
    private static final class Body {

        private final LogicSymbols.Signature signature;
        private final LogicSymbols symbols;
        private final DiagnosticCollector diagnostics;
        private final Deque<Map<String, Binding>> scopes = new ArrayDeque<>();
        private final Set<String> calls = new LinkedHashSet<>();
        private boolean valid = true;

        private Body(
                LogicSymbols.Signature signature,
                LogicSymbols symbols,
                DiagnosticCollector diagnostics) {
            this.signature = signature;
            this.symbols = symbols;
            this.diagnostics = diagnostics;
        }

        private Optional<LogicModel> analyze(LogicAst.Declaration declaration) {
            Map<String, Binding> parameters = new LinkedHashMap<>();
            for (LogicModel.Parameter parameter : signature.parameters()) {
                parameters.put(
                        parameter.name(),
                        new Binding(parameter.type(), parameter.where(), true));
            }
            scopes.push(parameters);
            List<TypedStatement> body = block(declaration.body());
            scopes.pop();

            if (valid && !TypedStatement.definitelyReturns(body)) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_LOGIC_MISSING_RETURN,
                        "Logic '" + signature.name()
                                + "' may finish without returning a value; every path must return "
                                + signature.returnType().display(),
                        declaration.where());
                valid = false;
            }
            if (!valid) {
                return Optional.empty();
            }
            return Optional.of(new LogicModel(
                    signature.name(),
                    signature.parameters(),
                    signature.returnType(),
                    body,
                    declaration.where()));
        }

        private List<TypedStatement> block(List<LogicAst.Statement> source) {
            scopes.push(new LinkedHashMap<>());
            List<TypedStatement> statements = new ArrayList<>();
            boolean returned = false;
            for (LogicAst.Statement statement : source) {
                if (returned) {
                    error(
                            ErrorCodes.SEMANTIC_LOGIC_UNREACHABLE,
                            "this statement can never be reached",
                            statement.where());
                    break;
                }
                Optional<TypedStatement> typed = statement(statement);
                if (typed.isEmpty()) {
                    break;
                }
                statements.add(typed.orElseThrow());
                returned = TypedStatement.definitelyReturns(List.of(typed.orElseThrow()));
            }
            reportUnused(scopes.pop());
            return statements;
        }

        private void reportUnused(Map<String, Binding> scope) {
            if (!valid) {
                return;
            }
            scope.forEach((name, binding) -> {
                if (!binding.parameter && !binding.used) {
                    diagnostics.warning(
                            ErrorCodes.SEMANTIC_LOGIC_UNUSED,
                            "value '" + name + "' is declared in Logic " + signature.name()
                                    + " but never used",
                            binding.where);
                }
            });
        }

        private Optional<TypedStatement> statement(LogicAst.Statement statement) {
            if (statement instanceof LogicAst.Assignment assignment) {
                return assignment(assignment);
            }
            if (statement instanceof LogicAst.Return returned) {
                return returned(returned);
            }
            if (statement instanceof LogicAst.Conditional conditional) {
                return conditional(conditional);
            }
            throw new IllegalStateException(
                    "unknown logic statement " + statement.getClass().getName());
        }

        private Optional<TypedStatement> assignment(LogicAst.Assignment assignment) {
            Optional<TypedExpression> value = expression(assignment.value());
            if (value.isEmpty()) {
                return Optional.empty();
            }
            Optional<Binding> existing = find(assignment.name());
            if (existing.isPresent()) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_REASSIGNMENT,
                        "'" + assignment.name() + "' is already bound at "
                                + location(existing.orElseThrow().where)
                                + "; Harpia Logic uses single assignment, so choose a new name",
                        assignment.where());
                return Optional.empty();
            }
            scopes.peek().put(
                    assignment.name(),
                    new Binding(value.orElseThrow().type(), assignment.where(), false));
            return Optional.of(new TypedStatement.Assignment(
                    assignment.name(), value.orElseThrow(), assignment.where()));
        }

        private Optional<TypedStatement> returned(LogicAst.Return returned) {
            Optional<TypedExpression> value = expression(returned.value());
            if (value.isEmpty()) {
                return Optional.empty();
            }
            LogicType actual = value.orElseThrow().type();
            if (!actual.assignableTo(signature.returnType())) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_RETURN_TYPE,
                        "Logic " + signature.name() + " declares output "
                                + signature.returnType().display() + " but returns "
                                + actual.display(),
                        returned.where());
                return Optional.empty();
            }
            return Optional.of(new TypedStatement.Return(value.orElseThrow(), returned.where()));
        }

        private Optional<TypedStatement> conditional(LogicAst.Conditional conditional) {
            Optional<TypedExpression> condition = expression(conditional.condition());
            if (condition.isEmpty()) {
                return Optional.empty();
            }
            if (!condition.orElseThrow().type().isBoolean()) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_TYPE,
                        "the condition of 'if' must be Boolean but was "
                                + condition.orElseThrow().type().display(),
                        conditional.condition().where());
                return Optional.empty();
            }
            List<TypedStatement> thenBranch = block(conditional.thenBranch());
            Optional<List<TypedStatement>> elseBranch =
                    conditional.elseBranch().map(this::block);
            if (!valid) {
                return Optional.empty();
            }
            return Optional.of(new TypedStatement.Conditional(
                    condition.orElseThrow(), thenBranch, elseBranch, conditional.where()));
        }

        private Optional<TypedExpression> expression(LogicAst.Expression expression) {
            if (expression instanceof LogicAst.Literal literal) {
                return Optional.of(literal(literal));
            }
            if (expression instanceof LogicAst.Reference reference) {
                return reference(reference);
            }
            if (expression instanceof LogicAst.Binary binary) {
                return binary(binary);
            }
            if (expression instanceof LogicAst.Unary unary) {
                return unary(unary);
            }
            if (expression instanceof LogicAst.Call call) {
                return call(call);
            }
            if (expression instanceof LogicAst.BuiltinCall call) {
                return builtin(call);
            }
            if (expression instanceof LogicAst.MemberAccess access) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_UNSUPPORTED,
                        "member access '." + access.member()
                                + "' requires a nominal type; Logic parameters are scalar in this"
                                + " version of Harpia Logic",
                        access.where());
                return Optional.empty();
            }
            throw new IllegalStateException(
                    "unknown logic expression " + expression.getClass().getName());
        }

        private TypedExpression literal(LogicAst.Literal literal) {
            LogicType type = switch (literal.kind()) {
                case INTEGER -> new BigInteger(literal.source()).compareTo(INT_MAX) > 0
                        ? LogicType.LONG
                        : LogicType.INT;
                case DECIMAL -> LogicType.DECIMAL;
                case BOOLEAN -> LogicType.BOOLEAN;
                case STRING -> LogicType.scalar(TypeRef.STRING);
            };
            return new TypedExpression.Literal(type, literal.source(), literal.where());
        }

        private Optional<TypedExpression> reference(LogicAst.Reference reference) {
            Optional<Binding> binding = find(reference.name());
            if (binding.isEmpty()) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME,
                        "unknown value '" + reference.name() + "'; it is not a parameter of Logic "
                                + signature.name() + " and was not assigned before this line",
                        reference.where());
                return Optional.empty();
            }
            binding.orElseThrow().used = true;
            return Optional.of(new TypedExpression.Variable(
                    reference.name(), binding.orElseThrow().type, reference.where()));
        }

        private Optional<TypedExpression> binary(LogicAst.Binary binary) {
            Optional<TypedExpression> left = expression(binary.left());
            Optional<TypedExpression> right = expression(binary.right());
            if (left.isEmpty() || right.isEmpty()) {
                return Optional.empty();
            }
            BinaryOperator operator = operator(binary.operator());
            LogicType leftType = left.orElseThrow().type();
            LogicType rightType = right.orElseThrow().type();

            Optional<LogicType> result;
            if (operator.isArithmetic()) {
                result = leftType.isNumeric() && rightType.isNumeric()
                        ? Optional.of(operator == BinaryOperator.DIVIDE
                                ? LogicType.DECIMAL
                                : LogicType.unify(leftType, rightType).orElseThrow())
                        : Optional.empty();
            } else if (operator.isOrdering()) {
                result = leftType.isNumeric() && rightType.isNumeric()
                        ? Optional.of(LogicType.BOOLEAN)
                        : Optional.empty();
            } else if (operator.isEquality()) {
                result = LogicType.unify(leftType, rightType).map(ignored -> LogicType.BOOLEAN);
            } else {
                result = leftType.isBoolean() && rightType.isBoolean()
                        ? Optional.of(LogicType.BOOLEAN)
                        : Optional.empty();
            }

            if (result.isEmpty()) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_TYPE,
                        "Cannot apply `" + operator.symbol() + "` to " + leftType.display()
                                + " and " + rightType.display() + ".",
                        binary.where());
                return Optional.empty();
            }
            return Optional.of(new TypedExpression.Binary(
                    operator,
                    left.orElseThrow(),
                    right.orElseThrow(),
                    result.orElseThrow(),
                    binary.where()));
        }

        private Optional<TypedExpression> unary(LogicAst.Unary unary) {
            Optional<TypedExpression> operand = expression(unary.operand());
            if (operand.isEmpty()) {
                return Optional.empty();
            }
            LogicType type = operand.orElseThrow().type();
            UnaryOperator operator = unary.operator().equals("not")
                    ? UnaryOperator.NOT
                    : UnaryOperator.NEGATE;
            boolean accepted = operator == UnaryOperator.NOT ? type.isBoolean() : type.isNumeric();
            if (!accepted) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_TYPE,
                        "Cannot apply `" + operator.symbol() + "` to " + type.display() + ".",
                        unary.where());
                return Optional.empty();
            }
            return Optional.of(new TypedExpression.Unary(
                    operator, operand.orElseThrow(), type, unary.where()));
        }

        private Optional<TypedExpression> call(LogicAst.Call call) {
            Optional<LogicSymbols.Signature> callee = symbols.lookup(call.name());
            if (callee.isEmpty()) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_FUNCTION,
                        "unknown Logic '" + call.name() + "'; declare it with '## Logic "
                                + call.name() + "'",
                        call.where());
                return Optional.empty();
            }
            calls.add(call.name());
            if (call.name().equals(signature.name())) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_RECURSION,
                        "Logic '" + signature.name() + "' cannot call itself; recursion is not "
                                + "supported, use '### Implementation custom'",
                        call.where());
                return Optional.empty();
            }

            Map<String, TypedExpression> arguments = new LinkedHashMap<>();
            for (LogicAst.NamedArgument argument : call.arguments()) {
                Optional<LogicModel.Parameter> parameter =
                        callee.orElseThrow().parameter(argument.name());
                if (parameter.isEmpty()) {
                    error(
                            ErrorCodes.SEMANTIC_LOGIC_ARGUMENT,
                            "Logic " + call.name() + " has no parameter '" + argument.name() + "'",
                            argument.where());
                    return Optional.empty();
                }
                Optional<TypedExpression> value = expression(argument.value());
                if (value.isEmpty()) {
                    return Optional.empty();
                }
                if (!value.orElseThrow().type().assignableTo(parameter.orElseThrow().type())) {
                    error(
                            ErrorCodes.SEMANTIC_LOGIC_ARGUMENT,
                            "argument '" + argument.name() + "' of " + call.name() + " expects "
                                    + parameter.orElseThrow().type().display() + " but was "
                                    + value.orElseThrow().type().display(),
                            argument.where());
                    return Optional.empty();
                }
                if (arguments.putIfAbsent(argument.name(), value.orElseThrow()) != null) {
                    error(
                            ErrorCodes.SEMANTIC_LOGIC_ARGUMENT,
                            "argument '" + argument.name() + "' of " + call.name()
                                    + " is provided more than once",
                            argument.where());
                    return Optional.empty();
                }
            }

            List<TypedExpression> ordered = new ArrayList<>();
            for (LogicModel.Parameter parameter : callee.orElseThrow().parameters()) {
                TypedExpression value = arguments.get(parameter.name());
                if (value == null) {
                    error(
                            ErrorCodes.SEMANTIC_LOGIC_ARGUMENT,
                            "call to " + call.name() + " is missing argument '"
                                    + parameter.name() + "'",
                            call.where());
                    return Optional.empty();
                }
                ordered.add(value);
            }
            return Optional.of(new TypedExpression.LogicCall(
                    call.name(),
                    ordered,
                    callee.orElseThrow().parameters().stream()
                            .map(LogicModel.Parameter::type)
                            .toList(),
                    callee.orElseThrow().returnType(),
                    call.where()));
        }

        private Optional<TypedExpression> builtin(LogicAst.BuiltinCall call) {
            Optional<BuiltinFunction> function = BuiltinRegistry.lookup(call.name());
            if (function.isEmpty()) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_FUNCTION,
                        "unknown function '" + call.name() + "'; built-ins are "
                                + BuiltinRegistry.names(),
                        call.where());
                return Optional.empty();
            }
            List<TypedExpression> arguments = new ArrayList<>();
            for (LogicAst.Expression argument : call.arguments()) {
                Optional<TypedExpression> value = expression(argument);
                if (value.isEmpty()) {
                    return Optional.empty();
                }
                arguments.add(value.orElseThrow());
            }
            Optional<LogicType> result = function.orElseThrow()
                    .resultType(arguments.stream().map(TypedExpression::type).toList());
            if (result.isEmpty()) {
                error(
                        ErrorCodes.SEMANTIC_LOGIC_ARGUMENT,
                        "built-in " + function.orElseThrow().describe()
                                + " does not accept "
                                + arguments.stream()
                                        .map(value -> value.type().display())
                                        .toList(),
                        call.where());
                return Optional.empty();
            }
            return Optional.of(new TypedExpression.BuiltinCall(
                    function.orElseThrow(), arguments, result.orElseThrow(), call.where()));
        }

        private BinaryOperator operator(String symbol) {
            return switch (symbol) {
                case "+" -> BinaryOperator.ADD;
                case "-" -> BinaryOperator.SUBTRACT;
                case "*" -> BinaryOperator.MULTIPLY;
                case "/" -> BinaryOperator.DIVIDE;
                case "==" -> BinaryOperator.EQUAL;
                case "!=" -> BinaryOperator.NOT_EQUAL;
                case "<" -> BinaryOperator.LESS;
                case "<=" -> BinaryOperator.LESS_OR_EQUAL;
                case ">" -> BinaryOperator.GREATER;
                case ">=" -> BinaryOperator.GREATER_OR_EQUAL;
                case "and" -> BinaryOperator.AND;
                case "or" -> BinaryOperator.OR;
                default -> throw new IllegalStateException("unknown operator " + symbol);
            };
        }

        private Optional<Binding> find(String name) {
            for (Map<String, Binding> scope : scopes) {
                Binding binding = scope.get(name);
                if (binding != null) {
                    return Optional.of(binding);
                }
            }
            return Optional.empty();
        }

        private void error(String code, String message, SourceRef where) {
            valid = false;
            diagnostics.error(code, message, where);
        }

        private static String location(SourceRef where) {
            return where.hasPosition()
                    ? where.file() + ":" + where.line() + ":" + where.column()
                    : where.file();
        }
    }

    private static final class Binding {
        private final LogicType type;
        private final SourceRef where;
        private final boolean parameter;
        private boolean used;

        private Binding(LogicType type, SourceRef where, boolean parameter) {
            this.type = type;
            this.where = where;
            this.parameter = parameter;
        }
    }
}
