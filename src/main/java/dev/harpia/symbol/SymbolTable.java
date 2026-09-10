package dev.harpia.symbol;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.logic.LogicType;
import dev.harpia.model.LogicModel;
import dev.harpia.model.Naming;
import dev.harpia.parse.LogicAst;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecAst;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every name the project declares, resolved in one deterministic declare pass.
 *
 * <p>Before this existed each validator kept its own map, so a name meant whatever the code
 * checking it happened to believe. One table means a reference resolves the same way whoever asks,
 * and it is what makes a cross-module reference resolvable at all — the pass visits modules in
 * discovery order, which is already sorted by path, so the result never depends on file order.
 *
 * <p>Resolution of what those names <em>contain</em> — field types, flow targets, call arguments —
 * is the second pass, and stays in the analyzers that own each construct.
 */
public final class SymbolTable {

    private final Map<Namespace, Map<String, Symbol>> symbols;

    private SymbolTable(Map<Namespace, Map<String, Symbol>> symbols) {
        EnumMap<Namespace, Map<String, Symbol>> copy = new EnumMap<>(Namespace.class);
        // Not Map.copyOf: it does not preserve insertion order, and the JDK randomises iteration
        // per JVM. Declaration order is discovery order, and inspection output has to be stable.
        symbols.forEach((namespace, entries) -> copy.put(
                namespace,
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries))));
        this.symbols = copy;
    }

    /** Declare pass. Every duplicate is reported once, against the declaration that repeats. */
    public static SymbolTable declare(
            ProjectAst project, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(diagnostics, "diagnostics");

        EnumMap<Namespace, Map<String, Symbol>> symbols = new EnumMap<>(Namespace.class);
        for (Namespace namespace : Namespace.values()) {
            symbols.put(namespace, new LinkedHashMap<>());
        }
        Set<String> reportedFirst = new HashSet<>();

        for (ModuleAst module : project.modules()) {
            if (module.declaresEntity()) {
                declare(
                        symbols,
                        new Symbol.Entity(module.entity().name(), module.file(), module.where()),
                        ErrorCodes.SEMANTIC_DUPLICATE_ENTITY,
                        "entity",
                        reportedFirst,
                        diagnostics);
            }
            for (SpecAst.EnumDeclaration declaration : module.enums()) {
                declare(
                        symbols,
                        new Symbol.EnumType(
                                declaration.name(),
                                module.file(),
                                declaration.values().stream()
                                        .map(SpecAst.EnumValue::name)
                                        .toList(),
                                declaration.where()),
                        ErrorCodes.SEMANTIC_DUPLICATE_ENTITY,
                        "type",
                        reportedFirst,
                        diagnostics);
            }
            for (SpecAst.ValueDeclaration declaration : module.values()) {
                declare(
                        symbols,
                        new Symbol.ValueType(
                                declaration.name(),
                                module.file(),
                                declaration.fields(),
                                declaration.where()),
                        ErrorCodes.SEMANTIC_DUPLICATE_ENTITY,
                        "type",
                        reportedFirst,
                        diagnostics);
            }
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                declare(
                        symbols,
                        new Symbol.Operation(
                                Naming.useCaseBaseName(useCase.title()),
                                module.file(),
                                useCase.title(),
                                useCase.where()),
                        ErrorCodes.SEMANTIC_DUPLICATE_USE_CASE,
                        "use-case name",
                        reportedFirst,
                        diagnostics);
            }
            for (dev.harpia.parse.IntegrationAst.Declaration integration
                    : module.integrations()) {
                declare(
                        symbols,
                        new Symbol.Integration(
                                integration.name(),
                                module.file(),
                                integration.operations(),
                                integration.where()),
                        ErrorCodes.SEMANTIC_DUPLICATE_INTEGRATION,
                        "integration",
                        reportedFirst,
                        diagnostics);
            }
            for (dev.harpia.parse.EventAst.Declaration event : module.events()) {
                declare(
                        symbols,
                        new Symbol.Event(
                                event.name(),
                                module.file(),
                                event.payload(),
                                event.where()),
                        ErrorCodes.SEMANTIC_DUPLICATE_EVENT,
                        "event",
                        reportedFirst,
                        diagnostics);
            }
            for (LogicAst.Declaration logic : module.logics()) {
                computation(logic, module.file(), diagnostics)
                        .ifPresent(symbol -> declare(
                                symbols,
                                symbol,
                                ErrorCodes.SEMANTIC_LOGIC_DUPLICATE,
                                "Logic",
                                reportedFirst,
                                diagnostics));
            }
            for (LogicAst.Scenario scenario : module.scenarios()) {
                declare(
                        symbols,
                        new Symbol.Scenario(
                                scenario.title(),
                                module.file(),
                                scenario.computation(),
                                scenario.where()),
                        ErrorCodes.SEMANTIC_SCENARIO_DUPLICATE,
                        "scenario",
                        reportedFirst,
                        diagnostics);
            }
        }
        return new SymbolTable(symbols);
    }

    private static void declare(
            Map<Namespace, Map<String, Symbol>> symbols,
            Symbol symbol,
            String code,
            String description,
            Set<String> reportedFirst,
            DiagnosticCollector diagnostics) {
        Map<String, Symbol> namespace = symbols.get(symbol.namespace());
        Symbol first = namespace.putIfAbsent(symbol.name(), symbol);
        if (first == null) {
            return;
        }
        // The first declaration is also flagged, once, so a duplicate is visible from either end.
        if (reportedFirst.add(symbol.namespace() + ":" + symbol.name())) {
            diagnostics.error(
                    code,
                    description + " '" + symbol.name() + "' is declared more than once",
                    first.where(),
                    "also declared here",
                    symbol.where());
        }
        diagnostics.error(
                code,
                "duplicate " + description + " '" + symbol.name() + "'",
                symbol.where(),
                "first declared here",
                first.where());
    }

    private static Optional<Symbol> computation(
            LogicAst.Declaration declaration, String module, DiagnosticCollector diagnostics) {
        List<LogicModel.Parameter> parameters = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (LogicAst.Parameter parameter : declaration.parameters()) {
            if (!names.add(parameter.name())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_LOGIC_DUPLICATE_PARAMETER,
                        "Logic " + declaration.name() + " declares parameter '"
                                + parameter.name() + "' more than once",
                        parameter.where());
                return Optional.empty();
            }
            parameters.add(new LogicModel.Parameter(
                    parameter.name(), LogicType.of(parameter.type()), parameter.where()));
        }
        return Optional.of(new Symbol.Computation(
                declaration.name(),
                module,
                parameters,
                LogicType.of(declaration.returnType()),
                declaration.where()));
    }

    public Optional<Symbol> lookup(Namespace namespace, String name) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(symbols.get(namespace).get(name));
    }

    public Optional<Symbol.Computation> computation(String name) {
        return lookup(Namespace.COMPUTATIONS, name)
                .filter(Symbol.Computation.class::isInstance)
                .map(Symbol.Computation.class::cast);
    }

    public Optional<Symbol.EnumType> enumType(String name) {
        return lookup(Namespace.TYPES, name)
                .filter(Symbol.EnumType.class::isInstance)
                .map(Symbol.EnumType.class::cast);
    }

    public Optional<Symbol.ValueType> valueType(String name) {
        return lookup(Namespace.TYPES, name)
                .filter(Symbol.ValueType.class::isInstance)
                .map(Symbol.ValueType.class::cast);
    }

    public Optional<Symbol.Entity> entity(String name) {
        return lookup(Namespace.TYPES, name)
                .filter(Symbol.Entity.class::isInstance)
                .map(Symbol.Entity.class::cast);
    }

    public Optional<Symbol.Event> event(String name) {
        return lookup(Namespace.EVENTS, name)
                .filter(Symbol.Event.class::isInstance)
                .map(Symbol.Event.class::cast);
    }

    public Optional<Symbol.Integration> integration(String name) {
        return lookup(Namespace.INTEGRATIONS, name)
                .filter(Symbol.Integration.class::isInstance)
                .map(Symbol.Integration.class::cast);
    }

    /** Declaration order inside each namespace, which is discovery order and therefore stable. */
    public List<Symbol> in(Namespace namespace) {
        return List.copyOf(symbols.get(namespace).values());
    }

}
