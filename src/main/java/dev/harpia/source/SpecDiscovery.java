package dev.harpia.source;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Deterministically discovers Harpia specs without following symbolic links. */
public final class SpecDiscovery {

    private static final String SPEC_SUFFIX = ".harpia.md";

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

        Path root = projectRoot.toAbsolutePath().normalize();
        Path specs = specsDirectory.isAbsolute()
                ? specsDirectory.toAbsolutePath().normalize()
                : root.resolve(specsDirectory).normalize();

        if (!specs.startsWith(root)) {
            diagnostics.error(
                    ErrorCodes.IO_PATH_ESCAPE,
                    "specs path escapes the project root: " + specsDirectory,
                    SourceRef.file(SourceFile.displayPath(root, specs)));
            return List.of();
        }
        if (Files.isSymbolicLink(specs)) {
            reportSymlink(root, specs, diagnostics);
            reportNoSpecs(root, specs, diagnostics);
            return List.of();
        }
        if (!Files.isDirectory(specs, LinkOption.NOFOLLOW_LINKS)) {
            reportNoSpecs(root, specs, diagnostics);
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(specs)) {
            List<Path> discovered = paths
                    .filter(path -> include(path, root, diagnostics))
                    .sorted(Comparator.comparing(path -> SourceFile.displayPath(root, path)))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
            if (discovered.isEmpty()) {
                reportNoSpecs(root, specs, diagnostics);
            }
            return discovered;
        } catch (IOException | UncheckedIOException | SecurityException exception) {
            diagnostics.error(
                    ErrorCodes.IO_FAILURE,
                    "could not discover specs: " + exception.getMessage(),
                    SourceRef.file(SourceFile.displayPath(root, specs)));
            return List.of();
        }
    }

    private static boolean include(Path path, Path root, DiagnosticCollector diagnostics) {
        if (Files.isSymbolicLink(path)) {
            reportSymlink(root, path, diagnostics);
            return false;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && path.getFileName().toString().endsWith(SPEC_SUFFIX);
    }

    private static void reportSymlink(Path root, Path path, DiagnosticCollector diagnostics) {
        diagnostics.warning(
                ErrorCodes.IO_SYMLINK_IGNORED,
                "symbolic link in specs was ignored",
                SourceRef.file(SourceFile.displayPath(root, path)));
    }

    private static void reportNoSpecs(Path root, Path specs, DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.CONFIG_NO_SPECS,
                "specs directory does not contain any *.harpia.md files",
                SourceRef.file(SourceFile.displayPath(root, specs)));
    }
}
