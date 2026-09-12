package dev.harpia.binding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PATCH, and the one thing that separates it from PUT.
 *
 * <p>A full update states the whole resource, so a field the request leaves out is a field set to
 * nothing. A partial update states only the changes, so the same omission leaves the stored value
 * alone. Every rule here is that sentence applied somewhere.
 */
class PartialUpdateTest {

    private static final String SERVICE =
            "src/main/java/com/example/patch/service/CustomerService.java";
    private static final String CONTROLLER =
            "src/main/java/com/example/patch/web/CustomerController.java";

    @TempDir
    Path projectRoot;

    @Test
    void anOmittedFieldSurvivesAPartialUpdate() throws IOException {
        project(1, "PATCH /customers/{id}", "- name: String\n- email: Email", true);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files().get(SERVICE))
                .as("a field the request omitted is not a change")
                .contains("""
                                if (request.name() != null) {
                                    customer.setName(request.name());
                                }
                                if (request.email() != null) {
                                    customer.setEmail(request.email());
                                }""".indent(8).stripTrailing());
    }

    @Test
    void aFullUpdateStillAssignsEveryField() throws IOException {
        project(1, "PUT /customers/{id}", "- name: String\n- email: Email", true);

        assertThat(compile().tree().orElseThrow().files().get(SERVICE))
                .as("PUT states the whole resource, so the omission is itself the statement")
                .contains("customer.setName(request.name());")
                .doesNotContain("if (request.name() != null)");
    }

    @Test
    void theBoundaryMapsPatchToItsOwnVerb() throws IOException {
        project(1, "PATCH /customers/{id}", "- name: String", true);

        CompileResult result = compile();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("HttpOperation PATCH /customers/{id} access=PUBLIC");
        assertThat(result.tree().orElseThrow().files().get(CONTROLLER))
                .contains("@PatchMapping(\"/customers/{id}\")")
                .contains("import org.springframework.web.bind.annotation.PatchMapping;");
    }

    @Test
    void aRequiredInputContradictsAPartialUpdate() throws IOException {
        project(1, "PATCH /customers/{id}", "- name: String required", true);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_PATCH_REQUIRED_INPUT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("input 'name' is required")
                        .contains("PATCH lets the request omit it"));
    }

    @Test
    void aPatchThatUpdatesNothingHasNoChangesToState() throws IOException {
        project(1, "PATCH /customers/{id}", "- name: String", false);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_PATCH_WITHOUT_UPDATE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("never updates from input"));
    }

    @Test
    void v0KeepsTheFourVerbsItAlwaysHad() throws IOException {
        project(0, "PATCH /customers/{id}", "- name: String", true);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_ENDPOINT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .isEqualTo("PATCH needs harpia.languageVersion 1"));
    }

    @Test
    void anEndpointVerbOutsideTheSetIsStillASyntaxError() throws IOException {
        project(1, "TRACE /customers/{id}", "- name: String", true);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_ENDPOINT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("expected GET|POST|PUT|PATCH|DELETE"));
    }

    @Test
    void theGeneratedProjectStillCompiles(@TempDir Path classes) throws IOException {
        project(1, "PATCH /customers/{id}", "- name: String\n- email: Email", true);

        // PatchMapping and MockMvc's patch builder are names, not strings; only javac knows
        // whether they resolve.
        GeneratedJava.compiles(compile().tree().orElseThrow().files(), classes);
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(int languageVersion, String endpoint, String input, boolean updates)
            throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required
                - email: Email required unique

                ## Patch Customer

                ### Endpoint

                %s

                ### Access

                public

                ### Input

                %s

                ### Flow

                ```flow
                customer = load Customer by id
                %ssave customer
                return customer
                ```

                ### Output

                200 Customer

                ### Errors

                - not found -> 404
                """.formatted(endpoint, input, updates ? "update customer from input\n" : ""),
                StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: %d

                project:
                  name: patch-service
                  group: com.example
                  artifact: patch-service
                  package: com.example.patch

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
                """.formatted(languageVersion), StandardCharsets.UTF_8);
    }
}
