package dev.harpia.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @TempDir
    Path projectRoot;

    @Test
    void loadsTheCanonicalConfigurationAndAppliesPathDefaults() throws IOException {
        writeConfig(validConfig().replace("paths:\n  specs: specs\n  output: generated\n", ""));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        HarpiaConfig config = ConfigLoader.load(projectRoot, diagnostics).orElseThrow();

        assertThat(config.harpia()).isEqualTo(1);
        assertThat(config.project().packageName()).isEqualTo("com.example.customer");
        assertThat(config.paths().specs()).isEqualTo("specs");
        assertThat(config.paths().output()).isEqualTo("generated");
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void reportsMalformedYamlWithItsPosition() throws IOException {
        writeConfig("harpia: 1\nproject: [unterminated\n");

        Diagnostic diagnostic = loadSingleDiagnostic(ErrorCodes.CONFIG_MALFORMED);

        assertThat(diagnostic.message()).startsWith("malformed YAML:");
        assertThat(diagnostic.where()).hasValueSatisfying(where -> {
            assertThat(where.file()).isEqualTo("harpia.yaml");
            assertThat(where.line()).isPositive();
            assertThat(where.column()).isPositive();
        });
    }

    @Test
    void reportsUnknownAndDuplicateKeysAtTheKey() throws IOException {
        writeConfig(validConfig()
                .replace("harpia: 1", "harpia: 1\nsecurity: enabled")
                .replace("  vendor: postgres", "  vendor: postgres\n  vendor: mysql"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(ConfigLoader.load(projectRoot, diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.CONFIG_UNKNOWN_KEY))
                .hasSize(2)
                .allSatisfy(diagnostic -> {
                    assertThat(diagnostic.message()).containsAnyOf("unknown key", "duplicate key");
                    assertThat(diagnostic.where()).hasValueSatisfying(where ->
                            assertThat(where.line()).isPositive());
                });
    }

    @Test
    void leavesNamespaceRulesToTheSelectedTarget() throws IOException {
        writeConfig(validConfig().replace("com.example.customer", "com.class.customer"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        HarpiaConfig config = ConfigLoader.load(projectRoot, diagnostics).orElseThrow();

        assertThat(config.project().packageName()).isEqualTo("com.class.customer");
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void reportsUnsupportedSchemaVersion() throws IOException {
        writeConfig(validConfig().replace("harpia: 1", "harpia: 2"));

        Diagnostic diagnostic = loadSingleDiagnostic(ErrorCodes.CONFIG_SCHEMA_VERSION);

        assertThat(diagnostic.message()).contains("must be 1");
        assertThat(diagnostic.where()).hasValueSatisfying(where ->
                assertThat(where.line()).isEqualTo(1));
    }

    @Test
    void reportsAnUnsupportedProviderValue() throws IOException {
        writeConfig(validConfig().replace("vendor: postgres", "vendor: mysql"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(ConfigLoader.load(projectRoot, diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.CONFIG_UNSUPPORTED_VALUE))
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.message()).contains("database.vendor");
                    assertThat(diagnostic.where()).hasValueSatisfying(where ->
                            assertThat(where.line()).isPositive());
                });
    }

    @Test
    void acceptsTheLegacyTargetBlockAndMapsItToTheJavaSpringIdentifier() throws IOException {
        writeConfig(validConfig().replace("""
                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.3.2"
                """, """
                target:
                  type: spring
                  javaVersion: 21
                  springBootVersion: 3.3.2
                """));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        HarpiaConfig config = ConfigLoader.load(projectRoot, diagnostics).orElseThrow();

        assertThat(config.target().id()).isEqualTo("java-spring");
        assertThat(config.target().languageVersion()).isEqualTo(21);
        assertThat(config.target().options()).containsEntry("springBootVersion", "3.3.2");
        assertThat(diagnostics.diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(ErrorCodes.CONFIG_DEPRECATED);
                    assertThat(diagnostic.isError()).isFalse();
                    assertThat(diagnostic.hint()).hasValueSatisfying(hint ->
                            assertThat(hint).contains("target.id"));
                });
    }

    @Test
    void rejectsMixingTheLegacyAndCanonicalTargetBlocks() throws IOException {
        writeConfig(validConfig().replace(
                "  id: java-spring", "  id: java-spring\n  type: spring"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(ConfigLoader.load(projectRoot, diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics())
                .extracting(Diagnostic::code)
                .contains(ErrorCodes.CONFIG_UNKNOWN_KEY);
    }

    @Test
    void keepsUnknownTargetOptionsUninterpreted() throws IOException {
        writeConfig(validConfig().replace(
                "    springBootVersion: \"3.3.2\"",
                "    springBootVersion: \"3.3.2\"\n    somethingOnlyTheTargetKnows: \"x\""));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        HarpiaConfig config = ConfigLoader.load(projectRoot, diagnostics).orElseThrow();

        assertThat(config.target().options())
                .containsEntry("somethingOnlyTheTargetKnows", "x");
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void reportsMissingConfiguration() {
        Diagnostic diagnostic = loadSingleDiagnostic(ErrorCodes.CONFIG_MISSING);

        assertThat(diagnostic.hint()).hasValueSatisfying(hint ->
                assertThat(hint).contains("harpia init"));
        assertThat(diagnostic.where()).hasValueSatisfying(where ->
                assertThat(where.file()).isEqualTo("harpia.yaml"));
    }

    private Diagnostic loadSingleDiagnostic(String expectedCode) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        Optional<HarpiaConfig> result = ConfigLoader.load(projectRoot, diagnostics);

        assertThat(result).isEmpty();
        return diagnostics.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code().equals(expectedCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected " + expectedCode + " in " + diagnostics.diagnostics()));
    }

    private void writeConfig(String content) throws IOException {
        Files.writeString(projectRoot.resolve("harpia.yaml"), content, StandardCharsets.UTF_8);
    }

    private String validConfig() {
        return """
                harpia: 1
                project:
                  name: customer-service
                  group: com.example
                  artifact: customer-service
                  package: com.example.customer
                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.3.2"
                database:
                  vendor: postgres
                paths:
                  specs: specs
                  output: generated
                generation:
                  migrations: true
                  tests: true
                """;
    }
}
