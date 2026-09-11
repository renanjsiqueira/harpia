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
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Named parameters anywhere in a path, not only a trailing {@code id}.
 *
 * <p>An inline endpoint has no mapping section, so a parameter is named after the value it carries
 * and the name is the mapping: {@code {tenant}} is filled by the input called {@code tenant}. Only
 * {@code id} means something else — the record the flow loads.
 */
class PathParameterTest {

    private static final String CONTROLLER =
            "src/main/java/com/example/shop/web/ItemController.java";
    private static final String CONTROLLER_TEST =
            "src/test/java/com/example/shop/web/ItemControllerTest.java";

    @TempDir
    Path projectRoot;

    @Test
    void aNamedParameterBindsToTheInputThatSharesItsName(@TempDir Path classes)
            throws IOException {
        project("GET /tenants/{tenant}/items/{id}", "- tenant: String required");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(CONTROLLER))
                .contains("@GetMapping(\"/tenants/{tenant}/items/{id}\")")
                .contains("@PathVariable(\"tenant\") String tenant")
                .contains("@PathVariable(\"id\") UUID id");
        assertThat(files.get(CONTROLLER_TEST))
                .as("a path expands one value per parameter, in the order they appear")
                .contains("get(\"/tenants/{tenant}/items/{id}\", \"tenant\", ID)");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aParameterWithNoInputToFillItIsRefused() throws IOException {
        project("GET /tenants/{tenant}/items/{id}", "");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_PATH_PARAM_INPUT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("path parameter '{tenant}' has no input 'tenant'"));
    }

    @Test
    void aTrailingIdStillWorksAsItAlwaysDid(@TempDir Path classes) throws IOException {
        project("GET /items/{id}", "");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files().get(CONTROLLER))
                .contains("@GetMapping(\"/items/{id}\")")
                .contains("@PathVariable(\"id\") UUID id");

        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }

    @Test
    void theRecordTheFlowLoadsCanSitAnywhereInThePath(@TempDir Path classes) throws IOException {
        project("GET /items/{id}/summary", "");

        CompileResult result = compile();
        assertThat(result.diagnostics())
                .as("'{id}' names the loaded record wherever it is written, not only last")
                .isEmpty();
        assertThat(result.tree().orElseThrow().files().get(CONTROLLER))
                .contains("@GetMapping(\"/items/{id}/summary\")")
                .contains("@PathVariable(\"id\") UUID id");

        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String endpoint, String input) throws IOException {
        String inputSection = input.isEmpty() ? "" : """
                ### Input

                """ + input + "\n";
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/item.harpia.md"), """
                # Item

                ## Data

                - id: UUID generated
                - tenant: String required unique
                - name: String required

                ## Query Get Item

                ### Endpoint

                """ + endpoint + """


                ### Access

                public

                """ + inputSection + """

                ### Flow

                ```flow
                item = load Item by id
                return item
                ```

                ### Output

                200 Item

                ### Errors

                - not found -> 404
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: shop-service
                  group: com.example
                  artifact: shop-service
                  package: com.example.shop

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
