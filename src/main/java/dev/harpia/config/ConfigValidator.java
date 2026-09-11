package dev.harpia.config;

import dev.harpia.LanguageVersion;
import dev.harpia.config.HarpiaConfig.DatabaseConfig;
import dev.harpia.config.HarpiaConfig.GenerationConfig;
import dev.harpia.config.HarpiaConfig.HarpiaSettings;
import dev.harpia.config.HarpiaConfig.PathsConfig;
import dev.harpia.config.HarpiaConfig.ProjectConfig;
import dev.harpia.config.HarpiaConfig.TargetConfig;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

/** Converts the YAML node tree to a typed config while accumulating independent errors. */
public final class ConfigValidator {

    private static final String FILE = ConfigLoader.CONFIG_FILE;
    private static final Set<String> ROOT_KEYS = Set.of(
            "harpia", "project", "target", "database", "paths", "generation");
    private static final Set<String> HARPIA_KEYS = Set.of("schemaVersion", "languageVersion");
    private static final Set<String> PROJECT_KEYS = Set.of("name", "group", "artifact", "package");
    private static final Set<String> TARGET_KEYS = Set.of(
            "id", "language", "options", "properties",
            "type", "javaVersion", "springBootVersion");
    private static final Set<String> TARGET_LANGUAGE_KEYS = Set.of("version");
    private static final String LEGACY_TARGET_HINT =
            "the canonical form is 'target.id', 'target.language.version' and 'target.options'";
    private static final Set<String> DATABASE_KEYS = Set.of("vendor");
    private static final Set<String> PATH_KEYS = Set.of("specs", "bindings", "output");
    private static final Set<String> GENERATION_KEYS = Set.of("migrations", "tests");

    private ConfigValidator() {
    }

    public static Optional<HarpiaConfig> validate(Node document, DiagnosticCollector diagnostics) {
        int errorsBefore = errorCount(diagnostics);
        if (document == null) {
            diagnostics.error(
                    ErrorCodes.CONFIG_MALFORMED,
                    "harpia.yaml must contain a YAML mapping",
                    SourceRef.of(FILE, 1, 1));
            return Optional.empty();
        }

        Map<String, Node> root = mapping(document, "root", ROOT_KEYS, diagnostics);
        Map<String, Node> harpia = requiredMapping(root, "harpia", HARPIA_KEYS, diagnostics);
        Integer schemaVersion = requiredInteger(harpia, "schemaVersion", diagnostics);
        if (schemaVersion != null && schemaVersion != 1) {
            errorAt(diagnostics, ErrorCodes.CONFIG_SCHEMA_VERSION,
                    "harpia.schemaVersion must be 1 (the only configuration schema supported)",
                    harpia.get("schemaVersion"));
        }
        Integer languageNumber = requiredInteger(harpia, "languageVersion", diagnostics);
        LanguageVersion languageVersion = languageNumber == null
                ? null
                : LanguageVersion.from(languageNumber).orElse(null);
        if (languageNumber != null && languageVersion == null) {
            errorAt(diagnostics, ErrorCodes.CONFIG_LANGUAGE_VERSION,
                    "harpia.languageVersion " + languageNumber
                            + " is not supported; supported versions: "
                            + LanguageVersion.supportedNumbers(),
                    harpia.get("languageVersion"));
        }

        Map<String, Node> project = requiredMapping(root, "project", PROJECT_KEYS, diagnostics);
        String name = requiredString(project, "name", diagnostics);
        String group = requiredString(project, "group", diagnostics);
        String artifact = requiredString(project, "artifact", diagnostics);
        String packageName = requiredString(project, "package", diagnostics);

        Map<String, Node> target = requiredMapping(root, "target", TARGET_KEYS, diagnostics);
        TargetConfig targetConfig = target(target, diagnostics);

        Map<String, Node> database = requiredMapping(root, "database", DATABASE_KEYS, diagnostics);
        String vendor = requiredString(database, "vendor", diagnostics);
        requireSupported("database.vendor", vendor, "postgres", database.get("vendor"), diagnostics);

        Map<String, Node> paths = optionalMapping(root, "paths", PATH_KEYS, diagnostics);
        String specs = optionalString(paths, "specs", "spec", diagnostics);
        String bindings = optionalString(paths, "bindings", "bindings", diagnostics);
        String output = optionalString(paths, "output", "generated", diagnostics);
        validateRelativePath("paths.specs", specs, paths.get("specs"), diagnostics);
        validateRelativePath("paths.bindings", bindings, paths.get("bindings"), diagnostics);
        validateRelativePath("paths.output", output, paths.get("output"), diagnostics);
        validateSeparateSourcePaths(specs, bindings, paths, diagnostics);

        Map<String, Node> generation = requiredMapping(root, "generation", GENERATION_KEYS, diagnostics);
        Boolean migrations = requiredBoolean(generation, "migrations", diagnostics);
        Boolean tests = requiredBoolean(generation, "tests", diagnostics);
        requireEnabled("generation.migrations", migrations, generation.get("migrations"), diagnostics);
        requireEnabled("generation.tests", tests, generation.get("tests"), diagnostics);

        if (errorCount(diagnostics) != errorsBefore) {
            return Optional.empty();
        }
        return Optional.of(new HarpiaConfig(
                new HarpiaSettings(schemaVersion, languageVersion),
                new ProjectConfig(name, group, artifact, packageName),
                targetConfig,
                new DatabaseConfig(vendor),
                new PathsConfig(specs, bindings, output),
                new GenerationConfig(migrations, tests)));
    }

    /**
     * Reads the target block in its canonical form and, for compatibility, in the legacy V0 form
     * {@code type: spring / javaVersion / springBootVersion}. The legacy form maps to the
     * {@code java-spring} identifier and is reported once as a deprecation warning.
     */
    private static TargetConfig target(Map<String, Node> target, DiagnosticCollector diagnostics) {
        boolean legacy = target.containsKey("type")
                || target.containsKey("javaVersion")
                || target.containsKey("springBootVersion");
        if (legacy && target.containsKey("id")) {
            errorAt(diagnostics, ErrorCodes.CONFIG_UNKNOWN_KEY,
                    "target.id cannot be combined with the legacy keys type/javaVersion/"
                            + "springBootVersion",
                    target.get("id"));
            return null;
        }

        Map<String, String> options = new LinkedHashMap<>();
        String id;
        Integer languageVersion;
        if (legacy) {
            warnAt(diagnostics, ErrorCodes.CONFIG_DEPRECATED,
                    "the 'target.type' configuration is deprecated",
                    target.get("type") == null ? target.get("javaVersion") : target.get("type"),
                    LEGACY_TARGET_HINT);
            String type = requiredString(target, "type", diagnostics);
            languageVersion = requiredInteger(target, "javaVersion", diagnostics);
            String springBootVersion = requiredString(target, "springBootVersion", diagnostics);
            if (type != null && !type.equals("spring")) {
                errorAt(diagnostics, ErrorCodes.CONFIG_UNSUPPORTED_VALUE,
                        "target.type must be 'spring'; use 'target.id' to select another target",
                        target.get("type"));
                return null;
            }
            id = "java-spring";
            if (springBootVersion != null) {
                options.put("springBootVersion", springBootVersion);
            }
        } else {
            id = requiredString(target, "id", diagnostics);
            Map<String, Node> language =
                    requiredMapping(target, "language", TARGET_LANGUAGE_KEYS, diagnostics);
            languageVersion = requiredInteger(language, "version", diagnostics);
            Map<String, Node> declared = optionalMapping(target, "options", null, diagnostics);
            for (Map.Entry<String, Node> option : declared.entrySet()) {
                String value = string(option.getValue(), "target.options." + option.getKey(),
                        diagnostics);
                if (value != null) {
                    options.put(option.getKey(), value);
                }
            }
        }

        Map<String, String> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Node> property
                : optionalMapping(target, "properties", null, diagnostics).entrySet()) {
            String key = property.getKey();
            // A property name is a path, and one that is not shaped like a path would never be
            // read by the framework it was written for: the file would look configured and be inert.
            if (!key.matches("[a-z][a-z0-9]*([-.][a-z0-9]+)*")) {
                errorAt(diagnostics, ErrorCodes.CONFIG_UNSUPPORTED_VALUE,
                        "target.properties key '" + key + "' is not a dotted lower-case property "
                                + "path",
                        property.getValue());
                continue;
            }
            String value = string(
                    property.getValue(), "target.properties." + key, diagnostics);
            if (value != null) {
                properties.put(key, value);
            }
        }

        if (id == null || languageVersion == null) {
            return null;
        }
        if (!id.matches("[a-z][a-z0-9]*(-[a-z0-9]+)*")) {
            errorAt(diagnostics, ErrorCodes.CONFIG_UNSUPPORTED_VALUE,
                    "target.id must be a lowercase, dash-separated identifier",
                    target.getOrDefault("id", target.get("type")));
            return null;
        }
        return new TargetConfig(id, languageVersion, options, properties);
    }

    private static Map<String, Node> requiredMapping(
            Map<String, Node> parent,
            String key,
            Set<String> allowed,
            DiagnosticCollector diagnostics) {
        Node node = parent.get(key);
        if (node == null) {
            missing(key, diagnostics);
            return Map.of();
        }
        return mapping(node, key, allowed, diagnostics);
    }

    private static Map<String, Node> optionalMapping(
            Map<String, Node> parent,
            String key,
            Set<String> allowed,
            DiagnosticCollector diagnostics) {
        Node node = parent.get(key);
        return node == null ? Map.of() : mapping(node, key, allowed, diagnostics);
    }

    private static Map<String, Node> mapping(
            Node node, String context, Set<String> allowed, DiagnosticCollector diagnostics) {
        if (!(node instanceof MappingNode mapping)) {
            errorAt(diagnostics, ErrorCodes.CONFIG_MISSING_KEY,
                    context + " must be a YAML mapping", node);
            return Map.of();
        }

        Map<String, Node> values = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                errorAt(diagnostics, ErrorCodes.CONFIG_MALFORMED,
                        "configuration keys must be scalar strings", tuple.getKeyNode());
                continue;
            }
            String key = keyNode.getValue();
            if (allowed != null && !allowed.contains(key)) {
                errorAt(diagnostics, ErrorCodes.CONFIG_UNKNOWN_KEY,
                        "unknown key '" + key + "' in " + context
                                + "; accepted keys: " + allowed.stream().sorted().toList(),
                        keyNode);
                continue;
            }
            if (values.putIfAbsent(key, tuple.getValueNode()) != null) {
                errorAt(diagnostics, ErrorCodes.CONFIG_UNKNOWN_KEY,
                        "duplicate key '" + key + "' in " + context, keyNode);
            }
        }
        return values;
    }

    private static String requiredString(
            Map<String, Node> values, String key, DiagnosticCollector diagnostics) {
        if (!values.containsKey(key)) {
            missing(key, diagnostics);
            return null;
        }
        return string(values.get(key), key, diagnostics);
    }

    private static String optionalString(
            Map<String, Node> values, String key, String defaultValue, DiagnosticCollector diagnostics) {
        return values.containsKey(key) ? string(values.get(key), key, diagnostics) : defaultValue;
    }

    private static String string(Node node, String key, DiagnosticCollector diagnostics) {
        if (!(node instanceof ScalarNode scalar) || scalar.getValue().isBlank()) {
            errorAt(diagnostics, ErrorCodes.CONFIG_MISSING_KEY,
                    key + " must be a non-empty scalar", node);
            return null;
        }
        return scalar.getValue();
    }

    private static Integer requiredInteger(
            Map<String, Node> values, String key, DiagnosticCollector diagnostics) {
        if (!values.containsKey(key)) {
            missing(key, diagnostics);
            return null;
        }
        return integer(values.get(key), key, diagnostics);
    }

    private static Integer integer(Node node, String key, DiagnosticCollector diagnostics) {
        if (node instanceof ScalarNode scalar && scalar.getValue().matches("-?[0-9]+")) {
            try {
                return Integer.valueOf(scalar.getValue());
            } catch (NumberFormatException ignored) {
                // Report the same stable type diagnostic as any other non-integer scalar.
            }
        }
        if (node != null) {
            errorAt(diagnostics, ErrorCodes.CONFIG_MISSING_KEY,
                    key + " must be an integer", node);
        }
        return null;
    }

    private static Boolean requiredBoolean(
            Map<String, Node> values, String key, DiagnosticCollector diagnostics) {
        if (!values.containsKey(key)) {
            missing(key, diagnostics);
            return null;
        }
        Node node = values.get(key);
        if (node instanceof ScalarNode scalar
                && (scalar.getValue().equals("true") || scalar.getValue().equals("false"))) {
            return Boolean.valueOf(scalar.getValue());
        }
        errorAt(diagnostics, ErrorCodes.CONFIG_MISSING_KEY,
                key + " must be true or false", node);
        return null;
    }

    private static void validateRelativePath(
            String key, String value, Node node, DiagnosticCollector diagnostics) {
        if (value == null) {
            return;
        }
        try {
            Path path = Path.of(value);
            if (path.isAbsolute() || path.normalize().startsWith("..")) {
                errorAt(diagnostics, ErrorCodes.IO_PATH_ESCAPE,
                        key + " must be relative and stay inside the project root", nodeOrStart(node));
            }
        } catch (InvalidPathException exception) {
            errorAt(diagnostics, ErrorCodes.IO_PATH_ESCAPE,
                    key + " is not a valid path", nodeOrStart(node));
        }
    }

    private static void validateSeparateSourcePaths(
            String specs,
            String bindings,
            Map<String, Node> paths,
            DiagnosticCollector diagnostics) {
        if (specs == null || bindings == null) {
            return;
        }
        try {
            if (Path.of(specs).normalize().equals(Path.of(bindings).normalize())) {
                Node where = paths.get("bindings") != null
                        ? paths.get("bindings")
                        : paths.get("specs");
                errorAt(
                        diagnostics,
                        ErrorCodes.CONFIG_UNSUPPORTED_VALUE,
                        "paths.specs and paths.bindings must be different directories",
                        where);
            }
        } catch (InvalidPathException ignored) {
            // validateRelativePath already reports the malformed value with its own key.
        }
    }

    private static void requireSupported(
            String key, String actual, String expected, Node node, DiagnosticCollector diagnostics) {
        if (actual != null && !actual.equals(expected)) {
            errorAt(diagnostics, ErrorCodes.CONFIG_UNSUPPORTED_VALUE,
                    key + " must be '" + expected + "' in Harpia V0", node);
        }
    }

    private static void requireEnabled(
            String key, Boolean value, Node node, DiagnosticCollector diagnostics) {
        if (Boolean.FALSE.equals(value)) {
            errorAt(diagnostics, ErrorCodes.CONFIG_UNSUPPORTED_VALUE,
                    key + " must be true in Harpia V0", node);
        }
    }

    private static void missing(String key, DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.CONFIG_MISSING_KEY,
                "required key '" + key + "' is missing",
                SourceRef.file(FILE));
    }

    private static void warnAt(
            DiagnosticCollector diagnostics, String code, String message, Node node, String hint) {
        Node location = nodeOrStart(node);
        int line = location.getStartMark() == null ? 1 : location.getStartMark().getLine() + 1;
        int column = location.getStartMark() == null ? 1 : location.getStartMark().getColumn() + 1;
        diagnostics.warning(code, message, SourceRef.of(FILE, line, column), hint);
    }

    private static void errorAt(
            DiagnosticCollector diagnostics, String code, String message, Node node) {
        Node location = nodeOrStart(node);
        int line = location.getStartMark() == null ? 1 : location.getStartMark().getLine() + 1;
        int column = location.getStartMark() == null ? 1 : location.getStartMark().getColumn() + 1;
        diagnostics.error(code, message, SourceRef.of(FILE, line, column));
    }

    private static Node nodeOrStart(Node node) {
        if (node != null) {
            return node;
        }
        throw new IllegalArgumentException("a source node is required for positioned diagnostics");
    }

    private static int errorCount(DiagnosticCollector diagnostics) {
        return (int) diagnostics.diagnostics().stream().filter(diagnostic -> diagnostic.isError()).count();
    }
}
