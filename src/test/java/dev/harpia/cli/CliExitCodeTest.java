package dev.harpia.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CliExitCodeTest {

    @TempDir
    Path projectRoot;

    @Test
    void validProjectExitsZeroAndKeepsStderrEmpty() throws IOException {
        writeValidProject();

        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout()).contains("Validation succeeded.");
        assertThat(execution.stderr()).isEmpty();
    }

    @Test
    void quietValidationSuppressesOnlyTheSuccessSummary() throws IOException {
        writeValidProject();

        Execution execution = execute("validate", "--quiet", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout()).isEmpty();
        assertThat(execution.stderr()).isEmpty();
    }

    @Test
    void buildWritesTheGeneratedTreeAndReportsWhatItDid() throws IOException {
        writeValidProject();

        Execution execution = execute("build", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .startsWith("Build succeeded: 11 files in generated (11 created, 0 updated, 0 unchanged).");
        assertThat(execution.stderr()).isEmpty();
        assertThat(projectRoot.resolve("generated/pom.xml")).exists();
        assertThat(projectRoot.resolve("generated/.harpia-manifest")).exists();
    }

    @Test
    void buildingTwiceRewritesNothing() throws IOException {
        writeValidProject();
        execute("build", "--dir", projectRoot.toString());

        Execution execution = execute("build", "--dir", projectRoot.toString());

        assertThat(execution.stdout())
                .startsWith("Build succeeded: 11 files in generated (0 created, 0 updated, 11 unchanged).");
    }

    @Test
    void validateStillWritesNothing() throws IOException {
        writeValidProject();

        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(projectRoot.resolve("generated")).doesNotExist();
    }

    @Test
    void invalidSpecEncodingIsACompilationError() throws IOException {
        writeValidConfig();
        Path source = projectRoot.resolve("specs/customer.harpia.md");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[] {(byte) 0xc3, (byte) 0x28});

        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(execution.stdout()).isEmpty();
        assertThat(execution.stderr())
                .contains("specs/customer.harpia.md: error[HRP5003]")
                .doesNotContain("Validation succeeded");
    }

    @Test
    void syntaxErrorIsACompilationErrorAndProducesNoSuccessOutput() throws IOException {
        writeValidConfig();
        writeSpec("# customer_name\n\n## Data\n\n- id: UUID generated\n");

        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(execution.stdout()).isEmpty();
        assertThat(execution.stderr()).contains("error[HRP1002]");
    }

    @Test
    void orphanEntityWarningKeepsExitZero() throws IOException {
        writeValidConfig();
        writeSpec("# Customer\n\n## Data\n\n- id: UUID generated\n");

        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout()).contains("Validation succeeded.");
        assertThat(execution.stderr()).contains("warning[HRP2017]");
    }

    @Test
    void missingYamlIsAStructuralError() {
        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.USAGE_OR_IO_ERROR);
        assertThat(execution.stderr()).contains("harpia.yaml: error[HRP3001]");
    }

    @Test
    void emptySpecsDirectoryIsAStructuralError() throws IOException {
        writeValidConfig();
        Files.createDirectories(projectRoot.resolve("specs"));

        Execution execution = execute("build", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.USAGE_OR_IO_ERROR);
        assertThat(execution.stderr()).contains("specs: error[HRP3005]");
        assertThat(execution.stdout()).isEmpty();
    }

    @Test
    void malformedCommandLineUsesPicocliExitTwo() {
        Execution execution = execute("validate", "--unknown-option");

        assertThat(execution.exitCode()).isEqualTo(ExitCode.USAGE_OR_IO_ERROR);
        assertThat(execution.stderr()).contains("Unknown option");
    }

    @Test
    void versionComesFromTheStaticBuildResource() {
        Execution execution = execute("version");

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout()).isEqualTo("harpia 0.1.0-SNAPSHOT\n");
        assertThat(execution.stderr()).isEmpty();
    }

    @Test
    void targetsListsWhatTheCompilerCanAndCannotGenerate() {
        Execution execution = execute("targets");

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("SUPPORTED")
                .contains("java-spring")
                .contains("NOT SUPPORTED")
                .contains("csharp-aspnet")
                .contains("elixir-phoenix")
                .contains("ruby-rails")
                .contains("typescript-nestjs");
    }

    @Test
    void targetsDescribesOneTargetIncludingItsCapabilities() {
        Execution execution = execute("targets", "java-spring");

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("Status: SUPPORTED")
                .contains("Java >= 21")
                .contains("Target version: 1")
                .contains("Template set: default (version 1)")
                .contains("persistence");
    }

    @Test
    void targetsRejectsAnIdentifierOutsideTheCatalogue() {
        Execution execution = execute("targets", "cobol-cics");

        assertThat(execution.exitCode()).isEqualTo(ExitCode.USAGE_OR_IO_ERROR);
        assertThat(execution.stderr()).contains("Unknown target: cobol-cics");
    }

    @Test
    void buildingAnUnsupportedTargetFailsAndGeneratesNothing() throws IOException {
        writeValidProject();
        Files.writeString(
                projectRoot.resolve("harpia.yaml"),
                Files.readString(projectRoot.resolve("harpia.yaml"), StandardCharsets.UTF_8)
                        .replace("id: java-spring", "id: csharp-aspnet"),
                StandardCharsets.UTF_8);

        Execution execution = execute("build", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(execution.stderr())
                .contains("HRP7001")
                .contains("no code was generated")
                .contains("supported targets: [java-spring]");
        assertThat(execution.stdout()).doesNotContain("Build succeeded");
    }

    @Test
    void inspectShowsTheStageThatWasAskedFor() throws IOException {
        writeValidProject();

        Execution execution = execute(
                "inspect", "--stage", "business-ir", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("Entity Customer")
                .contains("UseCase ListCustomers")
                .doesNotContain("Spring");
    }

    @Test
    void inspectRejectsAnUnknownStage() throws IOException {
        writeValidProject();

        Execution execution = execute(
                "inspect", "--stage", "java", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.USAGE_OR_IO_ERROR);
        assertThat(execution.stderr())
                .contains("Unknown stage: java")
                .contains("ast, symbols, business-ir, application-ir");
    }

    @Test
    void inspectReportsAStageTheCompilationNeverReached() throws IOException {
        writeValidConfig();
        writeSpec("# Customer\n\n## Data\n\n- id: UUID generated\n- id: UUID generated\n");

        Execution execution = execute(
                "inspect", "--stage", "business-ir", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isNotEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stderr()).contains("was not reached");
        assertThat(execution.stdout()).isEmpty();
    }

    @Test
    void aDuplicateShowsTheOtherPlaceOnItsOwnLine() throws IOException {
        writeValidConfig();
        writeSpec("""
                # Customer

                ## Data

                - id: UUID generated
                - name: String
                - name: String
                """);

        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(execution.stderr())
                .contains("error[HRP2002]: duplicate field 'name'")
                .contains("first declared here");
        assertThat(execution.stderr())
                .as("the terminal line stays the familiar file:line:column")
                .doesNotContain("-6:");
    }

    private Execution execute(String... args) {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        CommandLine commandLine = new CommandLine(new HarpiaCommand());
        commandLine.setOut(new PrintWriter(stdout, true));
        commandLine.setErr(new PrintWriter(stderr, true));

        int exitCode = commandLine.execute(args);
        return new Execution(exitCode, stdout.toString(), stderr.toString());
    }

    private void writeValidProject() throws IOException {
        writeValidConfig();
        writeSpec("""
                # Customer

                ## Data

                - id: UUID generated

                ## List Customers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Flow

                ```flow
                customers = list Customer
                return customers
                ```

                ### Output

                200 List<Customer>
                """);
    }

    private void writeSpec(String source) throws IOException {
        Path specs = Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(specs.resolve("customer.harpia.md"), source, StandardCharsets.UTF_8);
    }

    private void writeValidConfig() throws IOException {
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 0
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
                """, StandardCharsets.UTF_8);
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}
