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
import dev.harpia.parse.SpecAst;
import dev.harpia.model.Naming;
import dev.harpia.model.RuleModel;
import dev.harpia.model.ScenarioModel;
import dev.harpia.model.TypeRef;
import dev.harpia.parse.LogicAst;
import dev.harpia.parse.FieldLineParser;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.symbol.Symbol;
import dev.harpia.symbol.SymbolTable;
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
            ProjectAst project,
            SymbolTable symbols,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<LogicModel> models = new ArrayList<>();
        Map<String, Set<String>> callGraph = new LinkedHashMap<>();

        for (ModuleAst module : project.modules()) {
            for (LogicAst.Declaration declaration : module.logics()) {
                Optional<Symbol.Computation> signature = symbols.computation(declaration.name());
                if (signature.isEmpty()
                        || !signature.orElseThrow().where().equals(declaration.where())) {
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
                ScenarioAnalyzer.analyze(project, symbols, diagnostics),
                rules(project, symbols, diagnostics),
                invariants(project, symbols, diagnostics),
                guards(project, symbols, diagnostics),
                assignments(project, symbols, diagnostics));
    }

    /**
     * The typed invariants of every entity, by entity name.
     *
     * <p>A rule is a condition over what one operation was asked to do. An invariant is a condition
     * over what the entity is allowed to be, so its scope is the entity's own fields and it holds
     * whichever operation ran.
     */
    /**
     * The typed value of every {@code set}, by source position.
     *
     * <p>A set assigns to a field, so the field's type is what the expression must produce. Typing
     * it against that type is what turns "assign something" into "assign this".
     */
    public static Map<SourceRef, TypedExpression> assignments(
            ProjectAst project, SymbolTable symbols, DiagnosticCollector diagnostics) {
        Map<String, Map<String, SpecAst.FieldDeclaration>> entities = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            if (!module.declaresEntity()) {
                continue;
            }
            Map<String, SpecAst.FieldDeclaration> fields = new LinkedHashMap<>();
            module.entity().fields().forEach(field -> fields.putIfAbsent(field.name(), field));
            entities.put(module.entity().name(), fields);
        }

        Map<SourceRef, TypedExpression> typed = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                List<SpecAst.FlowStatement> statements = flattened(useCase.flow());
                boolean assigns = statements.stream().anyMatch(statement ->
                        statement instanceof SpecAst.SetField
                                || statement instanceof SpecAst.ChangeCollection);
                if (!assigns) {
                    continue;
                }
                Map<String, String> variables = new LinkedHashMap<>();
                for (SpecAst.FlowStatement statement : statements) {
                    if (statement instanceof SpecAst.CreateFrom create) {
                        variables.put(create.variable(), create.entity());
                    } else if (statement instanceof SpecAst.LoadById load) {
                        variables.put(load.variable(), load.entity());
                    } else if (statement instanceof SpecAst.FindBy find) {
                        variables.put(find.variable(), find.entity());
                    }
                }
                List<LogicModel.Parameter> scope = scopeOf(useCase.input());
                for (SpecAst.FlowStatement statement : statements) {
                    String variable;
                    String fieldName;
                    String text;
                    LogicAst.Expression expression;
                    boolean element;
                    if (statement instanceof SpecAst.SetField set) {
                        variable = set.variable();
                        fieldName = set.field();
                        text = set.text();
                        expression = set.value();
                        element = false;
                    } else if (statement instanceof SpecAst.ChangeCollection change) {
                        variable = change.variable();
                        fieldName = change.field();
                        text = change.text();
                        expression = change.element();
                        element = true;
                    } else {
                        continue;
                    }
                    SpecAst.FieldDeclaration field = Optional.ofNullable(variables.get(variable))
                            .map(entities::get)
                            .map(fields -> fields.get(fieldName))
                            .orElse(null);
                    if (field == null) {
                        continue;
                    }
                    // An element joins a collection, so what it must be is the collection's
                    // element type, not the collection.
                    String expected = element
                            ? FieldLineParser.elementOf(field.type()).orElse(null)
                            : field.type();
                    if (expected == null || !FieldLineParser.isScalar(expected)) {
                        // Nothing to type against; the structural error is reported elsewhere.
                        continue;
                    }
                    analyzeExpression(
                            "'" + text + "'",
                            scope,
                            LogicType.of(expected),
                            expression,
                            symbols,
                            statement.where(),
                            diagnostics)
                            .ifPresent(value -> typed.put(statement.where(), value));
                }
            }
        }
        return Map.copyOf(typed);
    }

    private static List<SpecAst.FlowStatement> flattened(List<SpecAst.FlowStatement> flow) {
        List<SpecAst.FlowStatement> result = new ArrayList<>();
        for (SpecAst.FlowStatement statement : flow) {
            result.add(statement);
            if (statement instanceof SpecAst.Conditional conditional) {
                result.addAll(flattened(conditional.whenTrue()));
                result.addAll(flattened(conditional.whenFalse()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * The typed condition of every guarded Flow instruction, by source position.
     *
     * <p>A guard is a boolean condition over the operation's input, which is the same thing a rule
     * is. Typing it through the same analysis keeps one definition of what an expression means.
     */
    public static Map<SourceRef, TypedExpression> guards(
            ProjectAst project, SymbolTable symbols, DiagnosticCollector diagnostics) {
        Map<SourceRef, TypedExpression> typed = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                List<LogicModel.Parameter> scope = scopeOf(useCase.input());
                conditions(useCase.flow(), scope, symbols, typed, diagnostics);
            }
        }
        return Map.copyOf(typed);
    }

    /**
     * The input values an expression may name.
     *
     * <p>Only scalars for now: the expression algebra has no member access, so a nominal-typed
     * input has nothing an expression could ask of it. Leaving it out means naming it reports an
     * unknown value, which is true.
     */
    private static List<LogicModel.Parameter> scopeOf(
            List<SpecAst.InputDeclaration> input) {
        List<LogicModel.Parameter> scope = new ArrayList<>();
        for (SpecAst.InputDeclaration field : input) {
            if (!dev.harpia.parse.FieldLineParser.isScalar(field.type())) {
                continue;
            }
            scope.add(new LogicModel.Parameter(
                    field.name(), LogicType.of(field.type()), field.where()));
        }
        return List.copyOf(scope);
    }

    /** Every boolean condition a flow states, at any nesting depth. */
    private static void conditions(
            List<SpecAst.FlowStatement> flow,
            List<LogicModel.Parameter> scope,
            SymbolTable symbols,
            Map<SourceRef, TypedExpression> typed,
            DiagnosticCollector diagnostics) {
        for (SpecAst.FlowStatement statement : flow) {
            if (statement instanceof SpecAst.Fail fail) {
                analyzeExpression(
                        "guard '" + fail.text() + "'",
                        scope,
                        LogicType.BOOLEAN,
                        fail.condition(),
                        symbols,
                        fail.where(),
                        diagnostics)
                        .ifPresent(expression -> typed.put(fail.where(), expression));
            } else if (statement instanceof SpecAst.Require require) {
                analyzeExpression(
                        "precondition '" + require.text() + "'",
                        scope,
                        LogicType.BOOLEAN,
                        require.condition(),
                        symbols,
                        require.where(),
                        diagnostics)
                        .ifPresent(expression -> typed.put(require.where(), expression));
            } else if (statement instanceof SpecAst.Conditional conditional) {
                analyzeExpression(
                        "condition '" + conditional.text() + "'",
                        scope,
                        LogicType.BOOLEAN,
                        conditional.condition(),
                        symbols,
                        conditional.where(),
                        diagnostics)
                        .ifPresent(expression -> typed.put(conditional.where(), expression));
                conditions(conditional.whenTrue(), scope, symbols, typed, diagnostics);
                conditions(conditional.whenFalse(), scope, symbols, typed, diagnostics);
            }
        }
    }

    private static Map<String, List<RuleModel>> invariants(
            ProjectAst project, SymbolTable symbols, DiagnosticCollector diagnostics) {
        Map<String, List<RuleModel>> byEntity = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            for (SpecAst.InvariantDeclaration declaration : module.invariants()) {
                if (!module.declaresEntity()) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_INVARIANT_WITHOUT_ENTITY,
                            "'## Invariants' needs a '## Data' in the same module to constrain",
                            declaration.where());
                    continue;
                }
                List<LogicModel.Parameter> scope = module.entity().fields().stream()
                        .map(field -> new LogicModel.Parameter(
                                field.name(), LogicType.of(field.type()), field.where()))
                        .toList();
                List<RuleModel> typed = new ArrayList<>();
                for (SpecAst.RuleDeclaration condition : declaration.conditions()) {
                    analyzeExpression(
                            "invariant '" + condition.text() + "'",
                            scope,
                            LogicType.BOOLEAN,
                            condition.condition(),
                            symbols,
                            condition.where(),
                            diagnostics)
                            .ifPresent(expression -> typed.add(new RuleModel(
                                    condition.text(), expression, condition.where())));
                }
                byEntity.put(module.entity().name(), List.copyOf(typed));
            }
        }
        return Map.copyOf(byEntity);
    }

    /**
     * The typed rules of every operation, by canonical operation symbol.
     *
     * <p>A rule is a boolean condition over the operation's input, which is exactly a Logic body
     * that takes those values and answers Boolean. Typing it here rather than beside the CRUD
     * checks keeps one definition of what an expression means in Harpia.
     */
    private static Map<String, List<RuleModel>> rules(
            ProjectAst project, SymbolTable symbols, DiagnosticCollector diagnostics) {
        Map<String, List<RuleModel>> byOperation = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                if (useCase.rules().isEmpty() || useCase.input().isEmpty()) {
                    continue;
                }
                List<LogicModel.Parameter> scope = scopeOf(useCase.input());
                List<RuleModel> typed = new ArrayList<>();
                for (SpecAst.RuleDeclaration rule : useCase.rules()) {
                    analyzeExpression(
                            "rule '" + rule.text() + "'",
                            scope,
                            LogicType.BOOLEAN,
                            rule.condition(),
                            symbols,
                            rule.where(),
                            diagnostics)
                            .ifPresent(condition -> typed.add(
                                    new RuleModel(rule.text(), condition, rule.where())));
                }
                byOperation.put(Naming.useCaseBaseName(useCase.title()), List.copyOf(typed));
            }
        }
        return Map.copyOf(byOperation);
    }

    /** Computations, the examples declared for them, and the rules of each operation. */
    public record Result(
            List<LogicModel> logics,
            List<ScenarioModel> scenarios,
            Map<String, List<RuleModel>> rules,
            Map<String, List<RuleModel>> invariants,
            Map<SourceRef, TypedExpression> guards,
            Map<SourceRef, TypedExpression> assignments) {
        public Result {
            rules = Map.copyOf(rules);
            invariants = Map.copyOf(invariants);
            guards = Map.copyOf(guards);
            assignments = Map.copyOf(assignments);
            logics = List.copyOf(logics);
            scenarios = List.copyOf(scenarios);
        }
    }

    /**
     * Types one standalone expression against a named scope.
     *
     * <p>A business rule is a boolean function of the values in scope, which is exactly what a
     * Logic body is. Typing it through the same analysis is therefore not reuse of convenience:
     * there is one definition of what an expression means in Harpia, and a rule cannot drift from
     * it.
     *
     * @param owner what the diagnostics should call the thing being analysed
     * @param scope the values the expression may name, in declaration order
     */
    public static Optional<TypedExpression> analyzeExpression(
            String owner,
            List<LogicModel.Parameter> scope,
            LogicType expected,
            LogicAst.Expression expression,
            SymbolTable symbols,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(diagnostics, "diagnostics");

        Body body = new Body(
                new Symbol.Computation(owner, "", scope, expected, where), symbols, diagnostics);
        Map<String, Binding> bindings = new LinkedHashMap<>();
        for (LogicModel.Parameter parameter : scope) {
            // Values in scope are given, not introduced here, so an unused one is not a warning.
            bindings.put(parameter.name(), new Binding(parameter.type(), parameter.where(), true));
        }
        body.scopes.push(bindings);
        Optional<TypedExpression> typed = body.expression(expression);
        body.scopes.pop();
        if (typed.isEmpty()) {
            return Optional.empty();
        }
        if (!typed.orElseThrow().type().assignableTo(expected)) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_LOGIC_TYPE,
                    owner + " must be " + expected.display() + " but is "
                            + typed.orElseThrow().type().display(),
                    expression.where());
            return Optional.empty();
        }
        return typed;
    }

    private static void reportCycles(
            Map<String, Set<String>> callGraph,
            SymbolTable symbols,
            DiagnosticCollector diagnostics) {
        Set<String> reported = new LinkedHashSet<>();
        for (String start : callGraph.keySet()) {
            List<String> path = new ArrayList<>();
            if (reachesItself(start, start, callGraph, path, new LinkedHashSet<>())
                    && reported.add(start)) {
                symbols.computation(start).ifPresent(signature -> diagnostics.error(
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

        private final Symbol.Computation signature;
        private final SymbolTable symbols;
        private final DiagnosticCollector diagnostics;
        private final Deque<Map<String, Binding>> scopes = new ArrayDeque<>();
        private final Set<String> calls = new LinkedHashSet<>();
        private boolean valid = true;

        private Body(
                Symbol.Computation signature,
                SymbolTable symbols,
                DiagnosticCollector diagnostics) {
            this.signature = signature;
            this.symbols = symbols;
            this.diagnostics = diagnostics;
        }

        private Optional<LogicModel> analyze(LogicAst.Declaration declaration) {
            // A custom computation declares a signature and delegates the algorithm. There is no
            // body to type, and inventing one to check would be checking a fiction.
            if (declaration.custom().isPresent()) {
                return Optional.of(new LogicModel(
                        signature.name(),
                        signature.parameters(),
                        signature.returnType(),
                        List.of(),
                        Optional.of(declaration.custom().orElseThrow().contract()),
                        declaration.where()));
            }
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
                valid = false;
                diagnostics.add(dev.harpia.diag.Diagnostic.error(
                                ErrorCodes.SEMANTIC_LOGIC_REASSIGNMENT,
                                "'" + assignment.name() + "' is already bound; Harpia Logic uses "
                                        + "single assignment, so choose a new name",
                                assignment.where())
                        .relatedTo("bound here", existing.orElseThrow().where));
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
            Optional<Symbol.Computation> callee = symbols.computation(call.name());
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
