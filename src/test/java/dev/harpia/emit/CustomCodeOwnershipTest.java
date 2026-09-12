package dev.harpia.emit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Code Harpia did not write is code Harpia does not own.
 *
 * <p>The escape hatch is only real if the implementation behind it survives. A generator that can
 * delete the user's work under any flag is a generator nobody can safely put custom code near, so
 * the guarantee has to hold at the strongest setting the CLI offers, not just the default one.
 */
class CustomCodeOwnershipTest {

    private static final String OUTPUT = "generated";
    private static final String CUSTOM =
            "src/main/java/com/example/risk/logic/DefaultRiskCalculator.java";
    private static final String IMPLEMENTATION = """
            package com.example.risk.logic;

            import java.math.BigDecimal;

            /** Written by a person. Harpia must never touch this file. */
            public class DefaultRiskCalculator implements RiskCalculator {

                @Override
                public BigDecimal apply(BigDecimal amount, Integer history) {
                    return amount.multiply(BigDecimal.valueOf(history));
                }
            }
            """;

    @TempDir
    Path projectRoot;

    @BeforeEach
    void writeProject() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/risk.harpia.md"), """
                # Risk

                ## Logic CalculateRisk

                ### Input

                - amount: Decimal
                - history: Int

                ### Output

                Decimal

                ### Implementation

                custom RiskCalculator
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: risk-service
                  group: com.example
                  artifact: risk-service
                  package: com.example.risk

                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.3.6"

                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """, StandardCharsets.UTF_8);
    }

    @Test
    void aCustomImplementationSurvivesACleanRebuildByteForByte() throws IOException {
        build(false, false);
        Path custom = projectRoot.resolve(OUTPUT).resolve(CUSTOM);
        Files.writeString(custom, IMPLEMENTATION, StandardCharsets.UTF_8);

        WriteReport report = build(true, true);

        assertThat(report.unknown())
                .as("Harpia reports what it does not own instead of assuming it is disposable")
                .contains(CUSTOM);
        assertThat(report.deleted()).doesNotContain(CUSTOM);
        assertThat(Files.readString(custom, StandardCharsets.UTF_8)).isEqualTo(IMPLEMENTATION);

        build(true, true);
        assertThat(Files.readString(custom, StandardCharsets.UTF_8))
                .as("two builds preserve the user's implementation byte for byte")
                .isEqualTo(IMPLEMENTATION);
    }

    @Test
    void theManifestNeverClaimsAFileHarpiaDidNotWrite() throws IOException {
        build(false, false);
        Files.writeString(
                projectRoot.resolve(OUTPUT).resolve(CUSTOM), IMPLEMENTATION, StandardCharsets.UTF_8);

        build(true, true);

        String manifest = Files.readString(
                projectRoot.resolve(OUTPUT).resolve(OutputWriter.MANIFEST), StandardCharsets.UTF_8);
        assertThat(manifest.lines())
                .as("ownership is what the manifest lists, so it must list only generated files")
                .doesNotContain(CUSTOM)
                .contains("src/main/java/com/example/risk/logic/RiskCalculator.java");
    }

    @Test
    void cleanWithoutForceRefusesRatherThanDecidingForTheUser() throws IOException {
        build(false, false);
        Files.writeString(
                projectRoot.resolve(OUTPUT).resolve(CUSTOM), IMPLEMENTATION, StandardCharsets.UTF_8);

        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.BUILD));
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        assertThat(OutputWriter.sync(
                        projectRoot,
                        result.outputDirectory().orElseThrow(),
                        result.tree().orElseThrow(),
                        true,
                        false,
                        diagnostics))
                .isEmpty();
        assertThat(diagnostics.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.IO_CLEAN_BLOCKED))
                .singleElement()
                .satisfies(diagnostic ->
                        assertThat(diagnostic.message()).contains("Harpia does not own"));
        assertThat(projectRoot.resolve(OUTPUT).resolve(CUSTOM)).exists();
    }

    @Test
    void aCustomDirectoryIsNeverPrunedForBeingUnknown() throws IOException {
        build(false, false);
        Path corner = projectRoot.resolve(OUTPUT).resolve("src/main/java/com/example/risk/custom");
        Files.createDirectories(corner);
        Files.writeString(corner.resolve("Notes.java"), "// mine\n", StandardCharsets.UTF_8);

        build(true, true);

        assertThat(corner.resolve("Notes.java")).exists();
        try (Stream<Path> files = Files.walk(projectRoot.resolve(OUTPUT))) {
            assertThat(files.anyMatch(path -> path.endsWith("Notes.java"))).isTrue();
        }
    }

    private WriteReport build(boolean clean, boolean force) {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.BUILD));
        assertThat(result.diagnostics()).isEmpty();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        return OutputWriter.sync(
                        projectRoot,
                        result.outputDirectory().orElseThrow(),
                        result.tree().orElseThrow(),
                        clean,
                        force,
                        diagnostics)
                .orElseThrow();
    }
}
