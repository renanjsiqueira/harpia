package dev.harpia.config;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.source.SourceFile;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;

/** Reads {@code harpia.yaml} with SnakeYAML's non-instantiating safe constructor. */
public final class ConfigLoader {

    public static final String CONFIG_FILE = "harpia.yaml";

    private ConfigLoader() {
    }

    public static Optional<HarpiaConfig> load(Path projectRoot, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(diagnostics, "diagnostics");

        Path root = projectRoot.toAbsolutePath().normalize();
        Path configPath = root.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(configPath)) {
            diagnostics.error(
                    ErrorCodes.CONFIG_MISSING,
                    "harpia.yaml was not found",
                    SourceRef.file(CONFIG_FILE),
                    "run 'harpia init' to create a project configuration");
            return Optional.empty();
        }

        Optional<SourceFile> source = SourceFile.read(root, configPath, diagnostics);
        if (source.isEmpty()) {
            if (!diagnostics.hasErrors()) {
                diagnostics.error(
                        ErrorCodes.IO_FAILURE,
                        "harpia.yaml could not be read as a regular file",
                        SourceRef.file(CONFIG_FILE));
            }
            return Optional.empty();
        }

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(true); // ConfigValidator reports every duplicate as HRP3003.
        options.setAllowRecursiveKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));

        try {
            Node document = yaml.compose(new StringReader(source.get().content()));
            return ConfigValidator.validate(document, diagnostics);
        } catch (MarkedYAMLException exception) {
            addMalformed(diagnostics, exception.getProblem(), exception.getProblemMark());
        } catch (YAMLException exception) {
            diagnostics.error(
                    ErrorCodes.CONFIG_MALFORMED,
                    "malformed YAML: " + safeMessage(exception),
                    SourceRef.of(CONFIG_FILE, 1, 1));
        }
        return Optional.empty();
    }

    private static void addMalformed(DiagnosticCollector diagnostics, String problem, Mark mark) {
        int line = mark == null ? 1 : mark.getLine() + 1;
        int column = mark == null ? 1 : mark.getColumn() + 1;
        String detail = problem == null || problem.isBlank() ? "invalid YAML syntax" : problem;
        diagnostics.error(
                ErrorCodes.CONFIG_MALFORMED,
                "malformed YAML: " + detail,
                SourceRef.of(CONFIG_FILE, line, column));
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
