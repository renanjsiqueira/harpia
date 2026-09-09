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
import java.util.stream.Stream;

/** Shared safe discovery for required specification and optional binding source trees. */
final class MarkdownDiscovery {

    private static final String SUFFIX = ".harpia.md";

    private MarkdownDiscovery() {
    }

    static List<Path> discover(
            Path projectRoot,
            Path directory,
            String kind,
            boolean required,
            DiagnosticCollector diagnostics) {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path sources = directory.isAbsolute()
                ? directory.toAbsolutePath().normalize()
                : root.resolve(directory).normalize();

        if (!sources.startsWith(root)) {
            diagnostics.error(
                    ErrorCodes.IO_PATH_ESCAPE,
                    kind + " path escapes the project root: " + directory,
                    SourceRef.file(SourceFile.displayPath(root, sources)));
            return List.of();
        }
        if (Files.isSymbolicLink(sources)) {
            reportSymlink(root, sources, kind, diagnostics);
            if (required) {
                reportEmpty(root, sources, kind, diagnostics);
            }
            return List.of();
        }
        if (!Files.isDirectory(sources, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                reportEmpty(root, sources, kind, diagnostics);
            }
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(sources)) {
            List<Path> discovered = paths
                    .filter(path -> include(path, root, kind, diagnostics))
                    .sorted(Comparator.comparing(path -> SourceFile.displayPath(root, path)))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
            if (required && discovered.isEmpty()) {
                reportEmpty(root, sources, kind, diagnostics);
            }
            return discovered;
        } catch (IOException | UncheckedIOException | SecurityException exception) {
            diagnostics.error(
                    ErrorCodes.IO_FAILURE,
                    "could not discover " + kind + ": " + exception.getMessage(),
                    SourceRef.file(SourceFile.displayPath(root, sources)));
            return List.of();
        }
    }

    private static boolean include(
            Path path, Path root, String kind, DiagnosticCollector diagnostics) {
        if (Files.isSymbolicLink(path)) {
            reportSymlink(root, path, kind, diagnostics);
            return false;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && path.getFileName().toString().endsWith(SUFFIX);
    }

    private static void reportSymlink(
            Path root, Path path, String kind, DiagnosticCollector diagnostics) {
        diagnostics.warning(
                ErrorCodes.IO_SYMLINK_IGNORED,
                "symbolic link in " + kind + " was ignored",
                SourceRef.file(SourceFile.displayPath(root, path)));
    }

    private static void reportEmpty(
            Path root, Path sources, String kind, DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.CONFIG_NO_SPECS,
                kind + " directory does not contain any *" + SUFFIX + " files",
                SourceRef.file(SourceFile.displayPath(root, sources)));
    }
}
