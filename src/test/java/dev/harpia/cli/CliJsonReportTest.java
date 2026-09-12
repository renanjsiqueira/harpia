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

/**
 * The machine-readable form of what a command decided.
 *
 * <p>The human output is arranged for someone reading a terminal. A caller that has to act on the
 * result should not have to parse prose, so {@code --json} puts one object on stdout and nothing
 * else: no summary line, and no diagnostics left on stderr to reassemble.
 */
class CliJsonReportTest {

    /**
     * Written as code points because a Java source file cannot hold either character: both are
     * line terminators to the compiler's own lexer, and a bell is not something to paste.
     */
    private static final char LINE_SEPARATOR = 0x2028;
    private static final char BELL = 0x0007;

    @TempDir
    Path projectRoot;

    @Test
    void aSuccessfulValidationIsOneObjectOnStdoutAndNothingElse() throws IOException {
        writeValidProject();

        Execution execution = execute("validate", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout()).isEqualTo(
                "{\"contract\":1,\"command\":\"validate\",\"ok\":true,\"exitCode\":0,"
                        + "\"diagnostics\":[]}\n");
        assertThat(execution.stderr())
                .as("stdout carries the whole answer, so nothing is left on stderr to reassemble")
                .isEmpty();
    }

    @Test
    void aRefusalCarriesItsCodeLocationAndSpan() throws IOException {
        writeValidConfig();
        writeSpec("# customer_name\n\n## Data\n\n- id: UUID generated\n");

        Execution execution = execute("validate", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(execution.stdout())
                .contains("\"ok\":false")
                .contains("\"exitCode\":1")
                .contains("\"severity\":\"error\"")
                .contains("\"code\":\"HRP1002\"")
                .contains("\"where\":{\"file\":\"specs/customer.harpia.md\",\"line\":1,"
                        + "\"column\":1,\"endLine\":1,\"endColumn\":16}");
        assertThat(execution.stderr()).isEmpty();
    }

    @Test
    void aSecondLocationIsDataAndNotProseInsideTheMessage() throws IOException {
        writeValidConfig();
        writeSpec("""
                # Customer

                ## Data

                - id: UUID generated
                - name: String
                - name: String
                """);

        Execution execution = execute("validate", "--json", "--dir", projectRoot.toString());

        assertThat(execution.stdout())
                .contains("\"related\":[{\"message\":\"first declared here\",\"where\":{"
                        + "\"file\":\"specs/customer.harpia.md\",\"line\":6,\"column\":1")
                .as("the message says what is wrong; where the other one is, is a field")
                .doesNotContain("first declared at");
    }

    @Test
    void aWarningKeepsTheRunSuccessfulAndStillAppears() throws IOException {
        writeValidConfig();
        writeSpec("# Customer\n\n## Data\n\n- id: UUID generated\n");

        Execution execution = execute("validate", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("\"ok\":true")
                .contains("\"severity\":\"warning\"")
                .contains("\"code\":\"HRP2017\"");
    }

    @Test
    void aBuildAlsoReportsWhatTheDirectoryNowHolds() throws IOException {
        writeValidProject();

        Execution execution = execute("build", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("\"command\":\"build\"")
                .contains("\"output\":{\"directory\":\"generated\",\"written\":11,\"created\":[")
                .contains("\"src/main/java/com/example/customer/domain/Customer.java\"")
                .contains("\"updated\":[],\"unchanged\":[]")
                .doesNotContain("Build succeeded");
    }

    @Test
    void buildingTwiceSaysNothingWasRewritten() throws IOException {
        writeValidProject();
        execute("build", "--json", "--dir", projectRoot.toString());

        Execution execution = execute("build", "--json", "--dir", projectRoot.toString());

        assertThat(execution.stdout())
                .contains("\"created\":[],\"updated\":[]")
                .contains("\"written\":11");
    }

    @Test
    void aBuildThatNeverCompiledReportsInTheSameShape() throws IOException {
        writeValidConfig();
        writeSpec("# customer_name\n\n## Data\n\n- id: UUID generated\n");

        Execution execution = execute("build", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(execution.stdout())
                .as("one shape for every ending, so a caller parses one thing")
                .startsWith("{\"contract\":1,\"command\":\"build\",\"ok\":false,\"exitCode\":1,")
                .doesNotContain("\"output\"");
        assertThat(execution.stderr()).isEmpty();
        assertThat(projectRoot.resolve("generated")).doesNotExist();
    }

    @Test
    void aMessageCarryingQuotesStaysReadableAsJson() {
        assertThat(Json.quote("a \"b\" \\ c\nd\te"))
                .isEqualTo("\"a \\\"b\\\" \\\\ c\\nd\\te\"");
        assertThat(Json.quote("line" + LINE_SEPARATOR + "separator"))
                .as("a raw U+2028 ends the string for a JavaScript parser reading the object")
                .isEqualTo("\"line\\u2028separator\"");
        assertThat(Json.quote("bel" + BELL))
                .as("a control character has no literal spelling in JSON")
                .isEqualTo("\"bel\\u0007\"");
        assertThat(Json.quote("acentuacao"))
                .isEqualTo("\"acentuacao\"");
    }

    @Test
    void inspectCarriesTheStageTextInsideTheSameEnvelope() throws IOException {
        writeValidProject();

        Execution execution = execute(
                "inspect", "--stage", "business-ir", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .startsWith("{\"contract\":1,\"command\":\"inspect\",\"ok\":true,\"exitCode\":0,")
                .contains("\"stage\":\"business-ir\"")
                .as("the rendering is text, so it arrives as one escaped string and not as lines")
                .contains("\"rendered\":\"Entity Customer\\n")
                .doesNotContain("Spring");
        assertThat(execution.stderr()).isEmpty();
    }

    @Test
    void aStageTheCompilationNeverReachedStillAnswersInShape() throws IOException {
        writeValidConfig();
        writeSpec("# Customer\n\n## Data\n\n- id: UUID generated\n- id: UUID generated\n");

        Execution execution = execute(
                "inspect", "--stage", "business-ir", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isNotEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("\"ok\":false")
                .as("empty is the honest answer for a stage nothing produced")
                .contains("\"rendered\":\"\"");
        assertThat(execution.stderr()).isEmpty();
    }

    @Test
    void targetsAreDataIncludingTheOnesThisCompilerCannotGenerate() {
        Execution execution = execute("targets", "--json");

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("\"id\":\"java-spring\"")
                .contains("\"canGenerate\":true")
                .contains("\"capabilities\":[\"http\",\"persistence\",\"security\"]")
                .contains("\"id\":\"csharp-aspnet\"")
                .as("a target with no generator declares no capabilities, and says so")
                .contains("\"canGenerate\":false");
    }

    @Test
    void anUnknownTargetIsAnsweredWithTheCatalogue() {
        Execution execution = execute("targets", "cobol-cics", "--json");

        assertThat(execution.exitCode()).isEqualTo(ExitCode.USAGE_OR_IO_ERROR);
        assertThat(execution.stdout())
                .contains("\"ok\":false")
                .contains("\"unknownTarget\":\"cobol-cics\"")
                .as("then which ones are there, is part of the same answer")
                .contains("\"id\":\"java-spring\"");
    }

    @Test
    void capabilitiesSayWhoSuppliesEachOneAndWhatAskedForIt() throws IOException {
        writeValidProject();

        Execution execution = execute("capabilities", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.SUCCESS);
        assertThat(execution.stdout())
                .contains("\"capability\":\"http\"")
                .as("an absent provider is not a missing one: the target supplies it itself")
                .contains("\"providedBy\":\"<target>\"")
                .contains("\"capability\":\"persistence\"")
                .contains("\"providedBy\":\"postgresql\"")
                .as("a requirement is only useful next to the declaration that created it")
                .contains("\"reason\":\"entity Customer declares persistent data\"");
        assertThat(execution.stderr()).isEmpty();
    }

    @Test
    void capabilitiesOfAProjectThatNeverCompiledAreNotReportedAsNone() throws IOException {
        writeValidConfig();
        writeSpec("# customer_name\n\n## Data\n\n- id: UUID generated\n");

        Execution execution = execute("capabilities", "--json", "--dir", projectRoot.toString());

        assertThat(execution.exitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(execution.stdout())
                .contains("\"ok\":false")
                .contains("\"capabilities\":[]")
                .contains("\"code\":\"HRP1002\"");
    }

    @Test
    void everyCommandThatReportsUsesTheSameContract() throws IOException {
        writeValidProject();

        assertThat(java.util.List.of(
                        execute("validate", "--json", "--dir", projectRoot.toString()),
                        execute("build", "--json", "--dir", projectRoot.toString()),
                        execute("inspect", "--stage", "ast", "--json",
                                "--dir", projectRoot.toString()),
                        execute("targets", "--json"),
                        execute("capabilities", "--json", "--dir", projectRoot.toString())))
                .allSatisfy(execution -> assertThat(execution.stdout())
                        .startsWith("{\"contract\":1,\"command\":\""));
    }

    @Test
    void withoutTheFlagNothingAboutTheHumanOutputChanges() throws IOException {
        writeValidProject();

        Execution execution = execute("validate", "--dir", projectRoot.toString());

        assertThat(execution.stdout()).isEqualTo("Validation succeeded.\n");
        assertThat(execution.stdout()).doesNotContain("\"contract\"");
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

    private void writeSpec(String content) throws IOException {
        Path source = projectRoot.resolve("specs/customer.harpia.md");
        Files.createDirectories(source.getParent());
        Files.writeString(source, content, StandardCharsets.UTF_8);
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}
