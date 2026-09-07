package dev.harpia.validate;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.model.LogicModel;
import dev.harpia.parse.LogicAst;
import dev.harpia.parse.SpecAst;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The computation namespace of the project, built in a declare pass so that a Logic may call
 * another Logic declared in any file, independently of discovery order.
 */
public final class LogicSymbols {

    private final Map<String, Signature> signatures;

    private LogicSymbols(Map<String, Signature> signatures) {
        this.signatures = Map.copyOf(signatures);
    }

    /** Declare pass. Duplicate names and duplicate parameters are reported here, once each. */
    public static LogicSymbols declare(
            List<SpecAst> specifications, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(specifications, "specifications");
        Objects.requireNonNull(diagnostics, "diagnostics");

        Map<String, Signature> signatures = new LinkedHashMap<>();
        for (SpecAst specification : specifications) {
            for (LogicAst.Declaration declaration : specification.logics()) {
                Optional<Signature> signature = signature(declaration, diagnostics);
                if (signature.isEmpty()) {
                    continue;
                }
                Signature first = signatures.putIfAbsent(
                        declaration.name(), signature.orElseThrow());
                if (first != null) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_LOGIC_DUPLICATE,
                            "duplicate Logic '" + declaration.name() + "'; first declared at "
                                    + location(first.where()),
                            declaration.where());
                }
            }
        }
        return new LogicSymbols(signatures);
    }

    private static Optional<Signature> signature(
            LogicAst.Declaration declaration, DiagnosticCollector diagnostics) {
        List<LogicModel.Parameter> parameters = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        boolean valid = true;
        for (LogicAst.Parameter parameter : declaration.parameters()) {
            if (!names.add(parameter.name())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_LOGIC_DUPLICATE_PARAMETER,
                        "Logic " + declaration.name() + " declares parameter '"
                                + parameter.name() + "' more than once",
                        parameter.where());
                valid = false;
                continue;
            }
            parameters.add(new LogicModel.Parameter(
                    parameter.name(), LogicType.of(parameter.type()), parameter.where()));
        }
        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new Signature(
                declaration.name(),
                parameters,
                LogicType.of(declaration.returnType()),
                declaration.where()));
    }

    public Optional<Signature> lookup(String name) {
        return Optional.ofNullable(signatures.get(name));
    }

    public boolean isDeclared(String name) {
        return signatures.containsKey(name);
    }

    private static String location(SourceRef where) {
        return where.hasPosition()
                ? where.file() + ":" + where.line() + ":" + where.column()
                : where.file();
    }

    public record Signature(
            String name,
            List<LogicModel.Parameter> parameters,
            LogicType returnType,
            SourceRef where) {
        public Signature {
            Objects.requireNonNull(name, "name");
            parameters = List.copyOf(parameters);
            Objects.requireNonNull(returnType, "returnType");
            Objects.requireNonNull(where, "where");
        }

        public Optional<LogicModel.Parameter> parameter(String parameterName) {
            return parameters.stream()
                    .filter(parameter -> parameter.name().equals(parameterName))
                    .findFirst();
        }
    }
}
