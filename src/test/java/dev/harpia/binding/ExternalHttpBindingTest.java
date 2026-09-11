package dev.harpia.binding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.emit.GeneratedFile;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalHttpBindingTest {

    @TempDir
    Path projectRoot;

    @BeforeEach
    void writeProject() throws IOException {
        Files.createDirectories(projectRoot.resolve("spec"));
        Files.writeString(projectRoot.resolve("spec/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## Query Get Customer

                ### Flow

                ```flow
                customer = load Customer by id
                return customer
                ```

                ### Output

                200 Customer

                ## Query List Customers

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
                  languageVersion: 1

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

                generation:
                  migrations: true
                  tests: true
                """, StandardCharsets.UTF_8);
    }

    @Test
    void anExternalBindingDeclaresAccessTheSameWayAnInlineEndpointDoes() throws IOException {
        bindings("""
                ## Bind GetCustomer

                ### Endpoint

                GET /customers/{id}

                ### Access

                role admin

                ### Request

                - id: path id

                ### Response

                output: body
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        // Where an operation is exposed from does not change what it asks of the caller: the
        // binding file and the inline endpoint go through the same access grammar.
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("access=ROLE admin");
        assertThat(result.tree().orElseThrow().files()
                        .get("src/main/java/com/example/customer/config/SecurityConfig.java"))
                .contains(".requestMatchers(HttpMethod.GET, \"/customers/{id}\")"
                        + ".hasAnyRole(\"ADMIN\")");
    }

    @Test
    void anExternalBindingExposesAnOtherwiseInternalOperation() throws IOException {
        bindings("""
                ## Bind GetCustomer

                ### Endpoint

                GET /customers/{id}

                ### Access

                public

                ### Request

                - id: path id

                ### Response

                output: body

                ## Bind ListCustomers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Request

                none

                ### Response

                output: body
                """);

        CompileResult result = compile();
        SortedMap<String, String> files = result.tree().orElseThrow().files();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("BindingFile bindings/http.harpia.md")
                .contains("Http GetCustomer GET /customers/{id} access=PUBLIC");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Query GetCustomer\n    Http GET /customers/{id}");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Capability http provider=<target>");
        assertThat(files.get(
                        "src/main/java/com/example/customer/web/CustomerController.java"))
                .contains("@GetMapping(\"/customers/{id}\")")
                .contains("@GetMapping(\"/customers\")");

        GeneratedFile controller = result.tree().orElseThrow().generatedFiles().stream()
                .filter(file -> file.relativePath().endsWith("/CustomerController.java"))
                .findFirst()
                .orElseThrow();
        assertThat(controller.sourceMappings())
                .filteredOn(mapping -> mapping.symbol().endsWith("#getCustomer"))
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.source().file()).isEqualTo("bindings/http.harpia.md");
                    assertThat(mapping.source().line()).isEqualTo(7);
                    assertThat(lineAt(controller.content(), mapping.generated().line()))
                            .contains("@GetMapping(\"/customers/{id}\")");
                });
    }

    @Test
    void aBindingMustReferenceADeclaredOperation() throws IOException {
        bindings(binding("MissingOperation", "GET /missing"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_BINDING_OPERATION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("unknown operation 'MissingOperation'"));
    }

    @Test
    void anInboundBaseUrlRemainsAPathPrefix() throws IOException {
        bindings("""
                ## Base URL

                https://api.example

                """ + binding("GetCustomer", "GET /customers/{id}"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_BINDING_MAPPING))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("inbound HTTP binding").contains("path-prefix"));
    }

    @Test
    void anOperationHasAtMostOneHttpBinding() throws IOException {
        bindings(binding("GetCustomer", "GET /customers/{id}")
                + binding("GetCustomer", "GET /customer/{id}"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_DUPLICATE_BINDING))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.related())
                        .singleElement());
    }

    @Test
    void routesRemainUniqueAcrossExternalBindings() throws IOException {
        bindings(binding("GetCustomer", "GET /customers/{id}")
                + binding("ListCustomers", "GET /customers/{id}"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_DUPLICATE_ROUTE))
                .singleElement();
    }

    @Test
    void pathParametersMustMatchTheBoundOperationsFlow() throws IOException {
        bindings(binding("GetCustomer", "GET /customers"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_PATH_VAR_FLOW))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("GetCustomer"));
    }

    @Test
    void queryAndHeaderMappingsReachTheGeneratedControllerUnderABaseUrl(@TempDir Path classes)
            throws IOException {
        Files.writeString(projectRoot.resolve("spec/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required
                - email: Email required

                ## Query Search Customers

                ### Input

                - name: String required
                - email: Email required

                ### Flow

                ```flow
                customers = list Customer
                return customers
                ```

                ### Output

                200 List<Customer>
                """, StandardCharsets.UTF_8);
        bindings("""
                ## Base URL

                /api/v1

                ## Bind SearchCustomers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Request

                - name: query name
                - email: header X-Email

                ### Response

                output: body
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        SortedMap<String, String> files = result.tree().orElseThrow().files();
        String controller = files.get(
                "src/main/java/com/example/customer/web/CustomerController.java");

        assertThat(controller)
                .contains("@GetMapping(\"/api/v1/customers\")")
                .contains("@RequestParam(\"name\") String name")
                .contains("@RequestHeader(\"X-Email\") String email")
                .as("the request type is named only inside a statement, so its import "
                        + "has to be declared rather than inferred from a parameter")
                .contains("import com.example.customer.dto.SearchCustomersRequest;");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void validationDeclaredInTheSpecificationIsEnforcedWhereTheRequestEnters(@TempDir Path classes)
            throws IOException {
        Files.writeString(projectRoot.resolve("spec/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required
                - email: Email required

                ## Query Search Customers

                ### Input

                - name: String required
                - email: Email required

                ### Flow

                ```flow
                validate input
                customers = list Customer
                return customers
                ```

                ### Output

                200 List<Customer>

                ### Errors

                - invalid input -> 400
                """, StandardCharsets.UTF_8);
        bindings("""
                ## Bind SearchCustomers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Request

                - name: query name
                - email: header X-Email

                ### Response

                output: body
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        SortedMap<String, String> files = result.tree().orElseThrow().files();

        assertThat(files.get("src/main/java/com/example/customer/web/CustomerController.java"))
                .as("a body is validated at this boundary; a parameter must be too")
                .contains("@Validated")
                .contains("@NotBlank @RequestParam(\"name\") String name")
                .contains("@Email @NotBlank @RequestHeader(\"X-Email\") String email");
        assertThat(files.get("src/main/java/com/example/customer/error/ApiExceptionHandler.java"))
                .as("method validation fails differently from body binding, "
                        + "but the declared status is the same")
                .contains("@ExceptionHandler(ConstraintViolationException.class)")
                .contains("ResponseEntity.status(400)");

        String controllerTest =
                files.get("src/test/java/com/example/customer/web/CustomerControllerTest.java");
        assertThat(controllerTest)
                .as("a query parameter is not JSON, so its sample value carries no quotes")
                .contains(".queryParam(\"name\", \"name\")")
                .contains(".header(\"X-Email\", \"email@example.com\")")
                .as("an empty body proves nothing about a request that has no body")
                .doesNotContain("RejectsAnIncompleteBody")
                .contains("void searchCustomersRejectsAnInvalidParameter()")
                .contains(".queryParam(\"name\", \"\")");

        GeneratedJava.compiles(files, classes);
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private static String lineAt(String content, int oneBasedLine) {
        return content.lines().skip(oneBasedLine - 1L).findFirst().orElseThrow();
    }

    private void bindings(String source) throws IOException {
        Files.createDirectories(projectRoot.resolve("bindings"));
        Files.writeString(
                projectRoot.resolve("bindings/http.harpia.md"),
                "# HTTP Bindings\n\n" + source,
                StandardCharsets.UTF_8);
    }

    private static String binding(String operation, String endpoint) {
        return """
                ## Bind %s

                ### Endpoint

                %s

                ### Access

                public

                ### Request

                %s

                ### Response

                output: body

                """.formatted(
                        operation,
                        endpoint,
                        endpoint.contains("{id}") ? "- id: path id" : "none");
    }
}
