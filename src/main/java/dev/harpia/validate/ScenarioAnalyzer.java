package dev.harpia.validate;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.model.Literal;
import dev.harpia.model.Literals;
import dev.harpia.model.LogicModel;
import dev.harpia.model.ScenarioModel;
import dev.harpia.parse.LogicAst;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.symbol.Symbol;
import dev.harpia.symbol.SymbolTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resolves each declared scenario against the computation it names. */
public final class ScenarioAnalyzer {

    private ScenarioAnalyzer() {
    }

    public static List<ScenarioModel> analyze(
            ProjectAst project,
            SymbolTable symbols,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(diagnostics, "diagnostics");

        Set<String> custom = new LinkedHashSet<>();
        for (ModuleAst module : project.modules()) {
            for (LogicAst.Declaration logic : module.logics()) {
                if (logic.custom().isPresent()) {
                    custom.add(logic.name());
                }
            }
        }

        List<ScenarioModel> scenarios = new ArrayList<>();
        for (ModuleAst module : project.modules()) {
            for (LogicAst.Scenario scenario : module.scenarios()) {
                // Harpia would have to run the user's implementation to know the answer, and it
                // does not run anything. A scenario here would assert against nothing.
                if (custom.contains(scenario.computation())) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_SCENARIO_CUSTOM,
                            "scenario '" + scenario.title() + "' targets Logic '"
                                    + scenario.computation()
                                    + "', whose implementation is custom; test it where it is"
                                    + " implemented",
                            scenario.computationWhere());
                    continue;
                }
                analyze(scenario, symbols, diagnostics).ifPresent(scenarios::add);
            }
        }
        return List.copyOf(scenarios);
    }

    private static Optional<ScenarioModel> analyze(
            LogicAst.Scenario scenario, SymbolTable symbols, DiagnosticCollector diagnostics) {
        Optional<Symbol.Computation> signature = symbols.computation(scenario.computation());
        if (signature.isEmpty()) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_SCENARIO_UNKNOWN_TARGET,
                    "scenario '" + scenario.title() + "' names unknown computation '"
                            + scenario.computation() + "'",
                    scenario.computationWhere());
            return Optional.empty();
        }
        Symbol.Computation computation = signature.orElseThrow();

        Map<String, LogicAst.Binding> given = new LinkedHashMap<>();
        boolean valid = true;
        Set<String> parameters = new LinkedHashSet<>(
                computation.parameters().stream().map(LogicModel.Parameter::name).toList());
        for (LogicAst.Binding binding : scenario.given()) {
            if (!parameters.contains(binding.name())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_SCENARIO_BINDING,
                        "computation " + computation.name() + " has no input '"
                                + binding.name() + "'",
                        binding.where());
                valid = false;
                continue;
            }
            if (given.putIfAbsent(binding.name(), binding) != null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_SCENARIO_BINDING,
                        "input '" + binding.name() + "' is given more than once",
                        binding.where());
                valid = false;
            }
        }

        List<ScenarioModel.Binding> arguments = new ArrayList<>();
        for (LogicModel.Parameter parameter : computation.parameters()) {
            LogicAst.Binding binding = given.get(parameter.name());
            if (binding == null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_SCENARIO_BINDING,
                        "scenario '" + scenario.title() + "' does not give input '"
                                + parameter.name() + "'",
                        scenario.where());
                valid = false;
                continue;
            }
            if (!accepts(parameter.type(), binding.literal())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_SCENARIO_VALUE,
                        "value '" + binding.literal() + "' is not a "
                                + parameter.type().display(),
                        binding.where());
                valid = false;
                continue;
            }
            arguments.add(new ScenarioModel.Binding(
                    parameter.name(),
                    parameter.type(),
                    new Literal(binding.literal()),
                    binding.where()));
        }

        if (!accepts(computation.returnType(), scenario.expected().literal())) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_SCENARIO_VALUE,
                    "result '" + scenario.expected().literal() + "' is not a "
                            + computation.returnType().display(),
                    scenario.expected().where());
            valid = false;
        }
        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new ScenarioModel(
                scenario.title(),
                methodName(scenario.title()),
                computation.name(),
                arguments,
                computation.returnType(),
                new Literal(scenario.expected().literal()),
                scenario.where()));
    }

    /** A scenario value may widen, exactly like an argument inside a computation. */
    private static boolean accepts(LogicType type, String literal) {
        if (!(type instanceof LogicType.Scalar scalar)) {
            return false;
        }
        if (Literals.matches(scalar.kind(), literal)) {
            return true;
        }
        return type.isNumeric()
                && Literals.matches(dev.harpia.model.TypeRef.INT, literal);
    }

    /** {@code VIP discount} becomes {@code vipDiscount}, a stable Java test method name. */
    static String methodName(String title) {
        StringBuilder result = new StringBuilder();
        for (String word : title.split("[^A-Za-z0-9]+")) {
            if (word.isEmpty()) {
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            result.append(result.isEmpty()
                    ? lower
                    : Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }
        return result.isEmpty() ? "scenario" : result.toString();
    }

    private static String location(SourceRef where) {
        return where.hasPosition()
                ? where.file() + ":" + where.line() + ":" + where.column()
                : where.file();
    }
}
