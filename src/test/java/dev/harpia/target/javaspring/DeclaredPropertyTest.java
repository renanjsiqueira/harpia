package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deployment configuration the project states for itself.
 *
 * <p>A property name belongs to a framework, not to a specification, so these live inside the
 * target block: nothing above the target boundary reads them. What the compiler does guarantee is
 * that they arrive in a deterministic order and that nothing the generated project depends on can
 * be quietly replaced by one.
 */
class DeclaredPropertyTest {

    private static final String CONFIG = "src/main/resources/application.yaml";

    @TempDir
    Path projectRoot;

    @Test
    void declaredPropertiesReachTheGeneratedConfiguration() throws IOException {
        project("""
                  properties:
                    server.port: "8081"
                    logging.level.root: "INFO"
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files().get(CONFIG))
                .contains("server.port: \"8081\"")
                .contains("logging.level.root: \"INFO\"")
                .as("what the provider decided is still there")
                .contains("spring.jpa.hibernate.ddl-auto: \"validate\"");
    }

    @Test
    void everyPropertyIsWrittenInOneOrderWhateverOrderItWasTypedIn() throws IOException {
        project("""
                  properties:
                    server.port: "8081"
                    logging.level.root: "INFO"
                """);
        String first = compile().tree().orElseThrow().files().get(CONFIG);

        project("""
                  properties:
                    logging.level.root: "INFO"
                    server.port: "8081"
                """);

        assertThat(compile().tree().orElseThrow().files().get(CONFIG))
                .as("a configuration file is diffed by machines and read by people")
                .isEqualTo(first);
    }

    @Test
    void aProjectWithNoDeclaredPropertiesIsUnchanged() throws IOException {
        project("");

        assertThat(compile().tree().orElseThrow().files().get(CONFIG))
                .contains("spring.application.name: \"customer-service\"")
                .doesNotContain("server.port");
    }

    @Test
    void whatTheGeneratedProjectDependsOnCannotBeQuietlyReplaced() throws IOException {
        project("""
                  properties:
                    spring.jpa.hibernate.ddl-auto: "update"
                """);

        // The provider chose `validate` to match the migrations Harpia generated. A project
        // flipping it to `update` would make those migrations a lie, so it is refused rather than
        // honoured — and refused as a diagnostic pointing at harpia.yaml, not as a stack trace.
        CompileResult result = compile();
        assertThat(result.tree()).isEmpty();
        assertThat(result.diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(ErrorCodes.CONFIG_UNSUPPORTED_VALUE);
                    assertThat(diagnostic.message())
                            .contains("target.properties cannot set "
                                    + "'spring.jpa.hibernate.ddl-auto'")
                            .contains("already sets it to 'validate'");
                    assertThat(diagnostic.where()).hasValueSatisfying(where ->
                            assertThat(where.file()).isEqualTo("harpia.yaml"));
                });
    }

    @Test
    void aKeyThatIsNotAPropertyPathIsRefused() throws IOException {
        project("""
                  properties:
                    Server_Port: "8081"
                """);

        // A name shaped like this would never be read by the framework it was written for: the
        // file would look configured and be inert.
        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.CONFIG_UNSUPPORTED_VALUE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("target.properties key 'Server_Port' is not a dotted "
                                + "lower-case property path"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String properties) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

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
                """, StandardCharsets.UTF_8);
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
                %s
                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """.formatted(properties), StandardCharsets.UTF_8);
    }
}
