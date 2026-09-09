package dev.harpia.source;

import dev.harpia.diag.DiagnosticCollector;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Deterministically discovers optional external binding sources without following symlinks. */
public final class BindingDiscovery {

    private BindingDiscovery() {
    }

    public static List<Path> discover(
            Path projectRoot, Path bindingsDirectory, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(bindingsDirectory, "bindingsDirectory");
        Objects.requireNonNull(diagnostics, "diagnostics");
        return MarkdownDiscovery.discover(
                projectRoot, bindingsDirectory, "bindings", false, diagnostics);
    }
}
