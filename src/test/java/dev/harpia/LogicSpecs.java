package dev.harpia;

import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.model.LogicModel;
import dev.harpia.model.ScenarioModel;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.source.SourceFile;
import dev.harpia.symbol.SymbolTable;
import dev.harpia.validate.LogicAnalyzer;
import java.util.List;
import java.util.Optional;

/** Builds and analyses small Logic modules so each test states only what it is about. */
public final class LogicSpecs {

    private LogicSpecs() {
    }

    /** Wraps one body into a module whose Logic is named {@code Sample}. */
    public static String module(String input, String output, String body) {
        return """
                # Pricing

                ## Logic Sample

                ### Input

                %s

                ### Output

                %s

                ```logic
                %s
                ```
                """.formatted(input, output, body);
    }

    public static Analysis analyze(String markdown) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        SourceFile source = new SourceFile("specs/pricing.harpia.md", markdown);
        Optional<ModuleAst> parsed = SpecParser.parse(source, LanguageVersion.V0, diagnostics);
        LogicAnalyzer.Result analysis = parsed
                .map(module -> {
                    ProjectAst project = new ProjectAst(LanguageVersion.V0, List.of(module));
                    return LogicAnalyzer.analyze(
                            project, SymbolTable.declare(project, diagnostics), diagnostics);
                })
                .orElse(new LogicAnalyzer.Result(List.of(), List.of(), java.util.Map.of()));
        return new Analysis(
                parsed, analysis.logics(), analysis.scenarios(), diagnostics.diagnostics());
    }

    /** Analyses a single Logic named {@code Sample}. */
    public static Analysis analyze(String input, String output, String body) {
        return analyze(module(input, output, body));
    }

    public record Analysis(
            Optional<ModuleAst> specification,
            List<LogicModel> logics,
            List<ScenarioModel> scenarios,
            List<Diagnostic> diagnostics) {

        public LogicModel single() {
            if (logics.size() != 1) {
                throw new IllegalStateException(
                        "expected one Logic but got " + logics.size() + " with " + diagnostics);
            }
            return logics.getFirst();
        }

        public List<String> codes() {
            return diagnostics.stream().map(Diagnostic::code).toList();
        }

        public String messages() {
            return diagnostics.stream().map(Diagnostic::message).toList().toString();
        }
    }
}
