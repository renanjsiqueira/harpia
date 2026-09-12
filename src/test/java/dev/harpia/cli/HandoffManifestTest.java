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
 * What a build leaves behind for whoever picks the directory up next.
 *
 * <p>Which target is this, what does it depend on, which files are mine to edit, and what did
 * Harpia deliberately leave unimplemented. Those answers existed inside the compiler and stopped at
 * the terminal; now they survive the build that produced them.
 */
class HandoffManifestTest {

    @TempDir
    Path projectRoot;

    @Test
    void aBuildLeavesTheAnswersNextToTheDirectoryItWrote() throws IOException {
        writeValidProject();

        assertThat(execute("build", "--dir", projectRoot.toString()))
                .isEqualTo(ExitCode.SUCCESS);

        assertThat(handoff())
                .contains("\"contract\":1")
                .contains("\"project\":{\"name\":\"customer-service\","
                        + "\"namespace\":\"com.example.customer\"}")
                .contains("\"target\":{\"id\":\"java-spring\",\"status\":\"SUPPORTED\"")
                .contains("\"capability\":\"persistence\",\"providedBy\":\"postgresql\"")
                .as("the target exposes endpoints itself; there is no provider to name")
                .contains("\"capability\":\"http\",\"providedBy\":\"<target>\"");
    }

    @Test
    void itPointsAtTheManifestInsteadOfCopyingIt() throws IOException {
        writeValidProject();
        execute("build", "--dir", projectRoot.toString());

        assertThat(handoff())
                .as("a second copy of the file list could disagree with the first")
                .contains("\"manifest\":\"generated/.harpia-manifest\"")
                .contains("\"files\":11")
                .doesNotContain("CustomerController.java");
    }

    @Test
    void itNamesTheWorkHarpiaDeliberatelyDidNotDo() throws IOException {
        writeValidConfig();
        writeSpec("""
                # Pricing

                ## Logic CalculateRisk

                ### Input

                - total: Decimal

                ### Output

                Decimal

                ### Implementation

                custom RiskCalculator
                """);

        assertThat(execute("build", "--dir", projectRoot.toString()))
                .isEqualTo(ExitCode.SUCCESS);
        assertThat(handoff())
                .as("the project looks finished and will not start without this")
                .contains("\"logic\":\"CalculateRisk\"")
                .contains("\"contract\":\"RiskCalculator\"")
                .contains("\"implement\":\"a bean implementing "
                        + "com.example.customer.logic.RiskCalculator\"");
    }

    @Test
    void aProjectThatImplementsEverythingSaysSo() throws IOException {
        writeValidProject();
        execute("build", "--dir", projectRoot.toString());

        assertThat(handoff())
                .as("empty is an answer; absent would be a question")
                .contains("\"customContracts\":[]");
    }

    @Test
    void itSaysWhichGatesPassedAndWhichAreStillYours() throws IOException {
        writeValidProject();
        execute("build", "--dir", projectRoot.toString());

        assertThat(handoff())
                .as("Harpia ran these two, so it reports them as facts")
                .contains("{\"gate\":\"validate\",\"runBy\":\"harpia\",\"status\":\"passed\"}")
                .contains("{\"gate\":\"build\",\"runBy\":\"harpia\",\"status\":\"passed\"}")
                .as("claiming these passed would invent a result nobody produced")
                .contains("{\"gate\":\"test\",\"runBy\":\"you\",\"status\":\"pending\","
                        + "\"command\":\"mvn -f generated test\"}")
                .contains("{\"gate\":\"package\",\"runBy\":\"you\",\"status\":\"pending\","
                        + "\"command\":\"mvn -f generated package\"}");
    }

    @Test
    void theNextStepIsTheFirstPendingGate() throws IOException {
        writeValidProject();
        execute("build", "--dir", projectRoot.toString());

        assertThat(handoff())
                .contains("\"next\":\"continue in generated; run: mvn -f generated test\"");
    }

    @Test
    void anUnimplementedContractComesBeforeAnyGate() throws IOException {
        writeValidConfig();
        writeSpec("""
                # Pricing

                ## Logic CalculateRisk

                ### Input

                - total: Decimal

                ### Output

                Decimal

                ### Implementation

                custom RiskCalculator
                """);
        execute("build", "--dir", projectRoot.toString());

        // Running the tests first would only produce a failure that says less than this does.
        assertThat(handoff())
                .contains("\"next\":\"implement com.example.customer.logic.RiskCalculator, "
                        + "then run the pending gates in generated\"");
    }

    @Test
    void theJsonBuildReportPointsAtTheHandoffRatherThanRepeatingIt() throws IOException {
        writeValidProject();
        StringWriter stdout = new StringWriter();
        CommandLine commandLine = new CommandLine(new HarpiaCommand());
        commandLine.setOut(new PrintWriter(stdout, true));
        commandLine.setErr(new PrintWriter(new StringWriter(), true));
        commandLine.execute("build", "--json", "--dir", projectRoot.toString());

        assertThat(stdout.toString())
                .as("a second copy of the gates could disagree with the first")
                .contains("\"handoff\":\".harpia/handoff.json\"")
                .doesNotContain("\"gate\":");
    }

    @Test
    void twoIdenticalBuildsLeaveIdenticalBytes() throws IOException {
        writeValidProject();
        execute("build", "--dir", projectRoot.toString());
        String first = handoff();

        execute("build", "--dir", projectRoot.toString());

        assertThat(handoff())
                .as("a clock here would be the one thing making a rebuild look like a change")
                .isEqualTo(first);
    }

    @Test
    void aBuildThatNeverCompiledLeavesNoHandoff() throws IOException {
        writeValidConfig();
        writeSpec("# customer_name\n\n## Data\n\n- id: UUID generated\n");

        assertThat(execute("build", "--dir", projectRoot.toString()))
                .isEqualTo(ExitCode.COMPILATION_ERROR);
        assertThat(projectRoot.resolve(HandoffManifest.PATH))
                .as("nothing was handed off, so there is nothing to say about it")
                .doesNotExist();
    }

    @Test
    void validateWritesNothingAtAll() throws IOException {
        writeValidProject();

        assertThat(execute("validate", "--dir", projectRoot.toString()))
                .isEqualTo(ExitCode.SUCCESS);
        assertThat(projectRoot.resolve(HandoffManifest.PATH)).doesNotExist();
        assertThat(projectRoot.resolve("generated")).doesNotExist();
    }

    private String handoff() throws IOException {
        return Files.readString(projectRoot.resolve(HandoffManifest.PATH), StandardCharsets.UTF_8);
    }

    private int execute(String... args) {
        CommandLine commandLine = new CommandLine(new HarpiaCommand());
        commandLine.setOut(new PrintWriter(new StringWriter(), true));
        commandLine.setErr(new PrintWriter(new StringWriter(), true));
        return commandLine.execute(args);
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
}
