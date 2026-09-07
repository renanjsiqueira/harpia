package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the multi-target boundary honest.
 *
 * <p>Harpia semantics are target independent. This test reads the production sources and fails if a
 * layer above the target boundary starts naming a language, a framework or a build tool. It is the
 * cheapest possible substitute for splitting the compiler into Maven modules, and unlike a
 * convention it cannot be forgotten.
 */
class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/dev/harpia");

    /**
     * Vocabulary that only the target layer may use. The compiler is itself written in Java, so
     * this list names frameworks and architectures rather than the JDK: a leak is
     * {@code SpringRepository}, not {@code java.math.BigDecimal} used to parse a literal.
     */
    private static final List<String> TARGET_VOCABULARY = List.of(
            "spring", "jpa", "hibernate", "jakarta", "javax.persistence",
            "maven", "gradle", "aspnet", "nestjs", "fastapi", "restcontroller",
            "entitymanager", "repository", "controller", "javatype");

    /** Target type names, which must never be produced or stored above the boundary. */
    private static final List<String> TARGET_TYPE_LITERALS = List.of(
            "\"BigDecimal\"", "\"OffsetDateTime\"", "\"LocalDate\"",
            "\"java.lang.", "\"java.math.", "\"java.time.", "\"java.util.");

    /** Packages that must stay target independent, and what they are. */
    private static final Map<String, String> INDEPENDENT_PACKAGES = Map.of(
            "source", "source loading",
            "ast", "markdown structure",
            "parse", "parser",
            "logic", "computation algebra",
            "validate", "semantic analysis",
            "model", "Business IR",
            "application", "Application IR",
            "capability", "capability model",
            "inspect", "pipeline inspection");

    @Test
    void noLayerAboveTheTargetBoundaryNamesALanguageOrAFramework() {
        List<String> violations = new ArrayList<>();
        INDEPENDENT_PACKAGES.forEach((directory, description) -> {
            for (Path file : sources(SOURCE_ROOT.resolve(directory))) {
                String source = withoutImports(read(file));
                String content = source.toLowerCase(Locale.ROOT);
                for (String word : TARGET_VOCABULARY) {
                    if (content.contains(word)) {
                        violations.add(description + ": " + file + " mentions '" + word + "'");
                    }
                }
                for (String literal : TARGET_TYPE_LITERALS) {
                    if (source.contains(literal)) {
                        violations.add(description + ": " + file + " produces " + literal);
                    }
                }
            }
        });

        assertThat(violations)
                .as("these layers describe intent, not an implementation")
                .isEmpty();
    }

    @Test
    void onlyTheTargetLayerImportsTheJavaSpringTarget() {
        List<String> violations = new ArrayList<>();
        for (Path file : sources(SOURCE_ROOT)) {
            String relative = SOURCE_ROOT.relativize(file).toString();
            if (relative.startsWith("target/") || relative.startsWith("cli/")) {
                continue;
            }
            if (read(file).contains("dev.harpia.target.javaspring")) {
                violations.add(relative);
            }
        }

        assertThat(violations)
                .as("the generator is reachable only through the target boundary")
                .isEmpty();
    }

    @Test
    void theTargetApiDoesNotDependOnAnySpecificTargetExceptToBootstrapTheRegistry() {
        Set<String> allowed = Set.of("TargetRegistry.java");
        List<String> violations = new ArrayList<>();
        Path targetRoot = SOURCE_ROOT.resolve("target");
        for (Path file : sources(targetRoot)) {
            String relative = targetRoot.relativize(file).toString().replace('\\', '/');
            if (relative.startsWith("javaspring/")) {
                continue;
            }
            if (!allowed.contains(file.getFileName().toString())
                    && read(file).contains("javaspring")) {
                violations.add(file.getFileName().toString());
            }
        }

        assertThat(violations)
                .as("only the registry knows which targets are built in")
                .isEmpty();
    }

    @Test
    void theBusinessIrDescribesIntentRatherThanArchitecture() {
        String businessIr = sources(SOURCE_ROOT.resolve("model")).stream()
                .map(ArchitectureBoundaryTest::read)
                .reduce("", String::concat)
                .toLowerCase(Locale.ROOT);

        assertThat(businessIr)
                .doesNotContain("tablename")
                .doesNotContain("columnname")
                .doesNotContain("service")
                .doesNotContain("endpoint binding");
    }

    /** The compiler runs on the JVM; importing the JDK is implementation, not a target choice. */
    private static String withoutImports(String source) {
        return source.lines()
                .filter(line -> !line.startsWith("import "))
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static List<Path> sources(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
