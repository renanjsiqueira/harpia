package dev.harpia.source;

import dev.harpia.diag.DiagnosticCollector;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Deterministically discovers Harpia specs without following symbolic links. */
public final class SpecDiscovery {

    private SpecDiscovery() {
    }

    /**
     * Returns absolute, normalized paths sorted by their project-relative {@code '/'} path.
     * Symbolic links are reported and excluded, whether they point to a file or a directory.
     */
    public static List<Path> discover(
            Path projectRoot, Path specsDirectory, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(specsDirectory, "specsDirectory");
        Objects.requireNonNull(diagnostics, "diagnostics");

        return MarkdownDiscovery.discover(
                projectRoot, specsDirectory, "specs", true, diagnostics);
    }
}
