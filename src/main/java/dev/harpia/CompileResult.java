package dev.harpia;

import dev.harpia.application.ApplicationProject;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticOrdering;
import dev.harpia.emit.GeneratedTree;
import dev.harpia.model.ProjectModel;
import dev.harpia.parse.ProjectAst;
import dev.harpia.symbol.SymbolTable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable compiler result. A generated tree exists only when compilation has no errors.
 *
 * <p>{@code outputDirectory} is the project-relative directory the tree belongs in. The compiler
 * itself never writes: it says where the result goes and lets the caller decide whether to.
 */
public record CompileResult(
        Optional<GeneratedTree> tree,
        Optional<String> outputDirectory,
        Optional<String> targetId,
        Stages stages,
        List<Diagnostic> diagnostics) {

    public CompileResult(Optional<GeneratedTree> tree, List<Diagnostic> diagnostics) {
        this(tree, Optional.empty(), Optional.empty(), Stages.none(), diagnostics);
    }

    public CompileResult(
            Optional<GeneratedTree> tree, Stages stages, List<Diagnostic> diagnostics) {
        this(tree, Optional.empty(), Optional.empty(), stages, diagnostics);
    }

    public CompileResult(
            Optional<GeneratedTree> tree,
            Optional<String> outputDirectory,
            Stages stages,
            List<Diagnostic> diagnostics) {
        this(tree, outputDirectory, Optional.empty(), stages, diagnostics);
    }

    public CompileResult {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        // Which target ran is a fact about this compilation, not about the Application IR, which
        // stays target independent on purpose. It sits here for the same reason the output
        // directory does: the caller asked for it and would otherwise have to re-read the config.
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = DiagnosticOrdering.sorted(diagnostics);
        if (diagnostics.stream().anyMatch(Diagnostic::isError) && tree.isPresent()) {
            throw new IllegalArgumentException("a failed compilation cannot expose a generated tree");
        }
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    /**
     * Every intermediate model the compilation reached, in pipeline order.
     *
     * <p>They are carried on the result rather than recomputed, so {@code harpia inspect} can only
     * ever show what the compiler actually produced. A parallel pipeline for inspection would be a
     * second source of truth, and the first thing to drift.
     */
    public record Stages(
            ProjectAst syntax,
            Optional<SymbolTable> symbols,
            Optional<ProjectModel> business,
            Optional<ApplicationProject> application) {

        public Stages {
            Objects.requireNonNull(syntax, "syntax");
            Objects.requireNonNull(symbols, "symbols");
            Objects.requireNonNull(business, "business");
            Objects.requireNonNull(application, "application");
        }

        public static Stages none() {
            return new Stages(
                    ProjectAst.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static Stages parsed(ProjectAst syntax) {
            return new Stages(syntax, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public Stages withSymbols(SymbolTable table) {
            return new Stages(syntax, Optional.of(table), business, application);
        }

        public Stages withBusiness(ProjectModel model) {
            return new Stages(syntax, symbols, Optional.of(model), application);
        }

        public Stages withApplication(ApplicationProject project) {
            return new Stages(syntax, symbols, business, Optional.of(project));
        }
    }
}
