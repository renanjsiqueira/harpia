package dev.harpia;

import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticOrdering;
import dev.harpia.application.ApplicationProject;
import dev.harpia.emit.GeneratedTree;
import dev.harpia.model.ProjectModel;
import dev.harpia.parse.SpecAst;
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
        Stages stages,
        List<Diagnostic> diagnostics) {

    public CompileResult(Optional<GeneratedTree> tree, List<Diagnostic> diagnostics) {
        this(tree, Optional.empty(), Stages.none(), diagnostics);
    }

    public CompileResult(
            Optional<GeneratedTree> tree, Stages stages, List<Diagnostic> diagnostics) {
        this(tree, Optional.empty(), stages, diagnostics);
    }

    public CompileResult {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
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
            List<SpecAst> modules,
            Optional<ProjectModel> business,
            Optional<ApplicationProject> application) {

        public Stages {
            modules = List.copyOf(modules);
            Objects.requireNonNull(business, "business");
            Objects.requireNonNull(application, "application");
        }

        public static Stages none() {
            return new Stages(List.of(), Optional.empty(), Optional.empty());
        }

        public Stages withBusiness(ProjectModel model) {
            return new Stages(modules, Optional.of(model), application);
        }

        public Stages withApplication(ApplicationProject project) {
            return new Stages(modules, business, Optional.of(project));
        }
    }
}
