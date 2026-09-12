package dev.harpia.binding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where an inline endpoint puts the inputs the path did not take.
 *
 * <p>GET and DELETE state what to act on, not a document to act with, so a body on either is
 * something no client sends. What those verbs carry goes in the query string; every other verb
 * keeps its body.
 */
class QueryParameterTest {

    private static final String CONTROLLER =
            "src/main/java/com/example/query/web/CustomerController.java";

    @TempDir
    Path projectRoot;

    @Test
    void aGetCarriesItsInputsInTheQueryString() throws IOException {
        project("""
                ## Query List Customers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                customers = list Customer by name
                return customers
                ```

                ### Output

                200 List<Customer>
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Request name from query name");
        assertThat(result.tree().orElseThrow().files().get(CONTROLLER))
                .as("a GET has no body to put an input in")
                .contains("@RequestParam(\"name\") String name")
                .doesNotContain("@RequestBody");
    }

    @Test
    void aPagedListingIsFilteredFromTheUrl() throws IOException {
        project("""
                ## Query List Customers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Input

                - page: Int required
                - size: Int required

                ### Flow

                ```flow
                customers = list Customer paged
                return customers
                ```

                ### Output

                200 Page<Customer>
                """);

        assertThat(compile().tree().orElseThrow().files().get(CONTROLLER))
                .as("a page is asked for in the URL, which is what makes the listing linkable")
                .contains("@RequestParam(\"page\") Integer page")
                .contains("@RequestParam(\"size\") Integer size");
    }

    @Test
    void aPostStillCarriesItsInputsInTheBody() throws IOException {
        project("""
                ## Command Create Customer

                ### Endpoint

                POST /customers

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                customer = create Customer from input
                save customer
                return customer
                ```

                ### Output

                201 Customer

                ### Errors

                - invalid input -> 400
                """);

        assertThat(compile().tree().orElseThrow().files().get(CONTROLLER))
                .as("a POST states a document to act with")
                .contains("@RequestBody CreateCustomerRequest request")
                .doesNotContain("@RequestParam");
    }

    @Test
    void anInputThePathAlreadyTookIsNotAskedForTwice() throws IOException {
        project("""
                ## Query List Customers

                ### Endpoint

                GET /tenants/{name}/customers

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                customers = list Customer by name
                return customers
                ```

                ### Output

                200 List<Customer>
                """);

        assertThat(compile().tree().orElseThrow().files().get(CONTROLLER))
                .contains("@PathVariable(\"name\") String name")
                .doesNotContain("@RequestParam");
    }

    @Test
    void theGeneratedProjectCompilesWithQueryParameters(@TempDir Path classes) throws IOException {
        project("""
                ## Query List Customers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                customers = list Customer by name
                return customers
                ```

                ### Output

                200 List<Customer>
                """);

        GeneratedJava.compiles(compile().tree().orElseThrow().files(), classes);
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String operation) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                """ + operation, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: query-service
                  group: com.example
                  artifact: query-service
                  package: com.example.query

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
