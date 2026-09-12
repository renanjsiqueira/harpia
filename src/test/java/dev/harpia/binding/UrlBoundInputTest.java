package dev.harpia.binding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a URL can carry.
 *
 * <p>A path segment holds one value written as text. A scalar is one value, and so is a declared
 * enum: a closed set of names. A collection is many values and has no spelling the URL agrees on,
 * so it is refused where the types are known instead of exploding inside a target.
 */
class UrlBoundInputTest {

    private static final String CONTROLLER =
            "src/main/java/com/example/url/web/CustomerController.java";

    @TempDir
    Path projectRoot;

    @Test
    void aDeclaredEnumIsAPathValueLikeAnyOther() throws IOException {
        project("Status", "- active\n- blocked");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files().get(CONTROLLER))
                .as("the enum is the parameter's type, not the scalar it is stored as")
                .contains("@PathVariable(\"status\") Status status")
                .contains("import com.example.url.domain.Status;");
    }

    @Test
    void theGeneratedProjectCompilesWithAnEnumInThePath(@TempDir Path classes) throws IOException {
        project("Status", "- active\n- blocked");

        // Before this was total, one enum in a path failed the whole target with HRP7007.
        GeneratedJava.compiles(compile().tree().orElseThrow().files(), classes);
    }

    @Test
    void theGeneratedRequestSpellsTheEnumTheSameWayTheBodyDoes() throws IOException {
        project("Status", "- active\n- blocked");

        assertThat(compile().tree().orElseThrow().files()
                        .get("src/test/java/com/example/url/web/CustomerControllerTest.java"))
                .as("a URL is a different place to write the value, not a different value")
                .contains("get(\"/tenants/{status}/customers\", \"ACTIVE\")");
    }

    @Test
    void aCollectionHasNoSpellingAUrlAgreesOn() throws IOException {
        project("List<String>", null);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_URL_BOUND_INPUT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("input 'status' is 'List<String>'")
                        .contains("a path or query value is a scalar or a declared enum"));
    }

    @Test
    void aScalarInThePathIsUntouched() throws IOException {
        project("String", null);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files().get(CONTROLLER))
                .contains("@PathVariable(\"status\") String status");
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String type, String enumValues) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                %s## Data

                - id: UUID generated
                - name: String required
                - status: %s required

                ## Query List Customers

                ### Endpoint

                GET /tenants/{status}/customers

                ### Access

                public

                ### Input

                - status: %s required

                ### Flow

                ```flow
                customers = list Customer by status
                return customers
                ```

                ### Output

                200 List<Customer>
                """.formatted(
                        enumValues == null ? "" : "## Enum Status\n\n" + enumValues + "\n\n",
                        type,
                        type),
                StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: url-service
                  group: com.example
                  artifact: url-service
                  package: com.example.url

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
}
