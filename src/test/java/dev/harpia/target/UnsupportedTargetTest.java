package dev.harpia.target;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The same specification, compiled for a target this compiler cannot generate.
 *
 * <p>The spec never changes between the supported and the unsupported case: that is the whole point
 * of the target boundary. What changes is one line of {@code harpia.yaml}.
 */
class UnsupportedTargetTest {

    @TempDir
    Path projectRoot;

    @Test
    void buildFailsWithoutGeneratingAnything() throws IOException {
        project("csharp-aspnet");

        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.BUILD));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.tree()).as("no code was generated").isEmpty();
        assertThat(diagnostic(result, ErrorCodes.TARGET_NOT_SUPPORTED)).satisfies(diagnostic -> {
            assertThat(diagnostic.isError()).isTrue();
            assertThat(diagnostic.message())
                    .contains("`csharp-aspnet`")
                    .contains("NOT_SUPPORTED")
                    .contains("no code was generated");
            assertThat(diagnostic.hint()).hasValueSatisfying(hint -> assertThat(hint)
                    .contains("supported targets: [java-spring]")
                    .contains("csharp-aspnet"));
        });
    }

    @Test
    void validateSeparatesASoundSpecificationFromAnUnavailableTarget() throws IOException {
        project("csharp-aspnet");

        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE));

        assertThat(result.hasErrors())
                .as("the specification itself is valid")
                .isFalse();
        assertThat(result.tree()).isEmpty();
        assertThat(diagnostic(result, ErrorCodes.TARGET_NOT_SUPPORTED)).satisfies(diagnostic -> {
            assertThat(diagnostic.isError()).isFalse();
            assertThat(diagnostic.message()).contains("the specification was still validated");
        });
    }

    @Test
    void neverFallsBackToTheSupportedTarget() throws IOException {
        project("python-fastapi");

        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));

        assertThat(result.tree()).isEmpty();
        assertThat(result.diagnostics())
                .extracting(Diagnostic::code)
                .containsExactly(ErrorCodes.TARGET_NOT_SUPPORTED);
    }

    @Test
    void anIdentifierOutsideTheCatalogueIsReportedAsUnknown() throws IOException {
        project("cobol-cics");

        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));

        assertThat(diagnostic(result, ErrorCodes.TARGET_UNKNOWN).message())
                .contains("unknown target `cobol-cics`");
    }

    @Test
    void aLanguageVersionBelowTheTargetRequirementIsRejectedByTheTargetNotTheConfig()
            throws IOException {
        write(config("java-spring").replace("version: 21", "version: 17"));

        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));

        assertThat(diagnostic(result, ErrorCodes.TARGET_LANGUAGE_VERSION).message())
                .contains("Java >= 21")
                .contains("language version is 17");
    }

    @Test
    void theSupportedTargetStillBuildsFromTheSameSpecification() throws IOException {
        project("java-spring");

        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.tree().orElseThrow().files().keySet())
                .contains("pom.xml", "src/main/resources/application.yaml");
    }

    private static Diagnostic diagnostic(CompileResult result, String code) {
        List<Diagnostic> matching = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code().equals(code))
                .toList();
        assertThat(matching).as("expected %s in %s", code, result.diagnostics()).hasSize(1);
        return matching.getFirst();
    }

    private void project(String targetId) throws IOException {
        write(config(targetId));
    }

    private void write(String configuration) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(
                projectRoot.resolve("specs/customer.harpia.md"),
                """
                # Customer

                ## Data

                - id: UUID generated
                - email: Email required unique

                ## Create Customer

                ### Endpoint

                POST /customers

                ### Access

                public

                ### Input

                - email: Email required

                ### Flow

                ```flow
                validate input
                customer = create Customer from input
                save customer
                return customer
                ```

                ### Output

                201 Customer
                """,
                StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), configuration, StandardCharsets.UTF_8);
    }

    private static String config(String targetId) {
        return """
                harpia: 1
                project:
                  name: customer-service
                  group: com.example
                  artifact: customer-service
                  package: com.example.customer
                target:
                  id: %s
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
                """.formatted(targetId);
    }
}
