package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
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
 * Access on a declared Command and a declared Query.
 *
 * <p>The access grammar belongs to the endpoint, not to the heading that declared the operation, so
 * a {@code ## Command} asks for an identity the same way a legacy use case does. Worth its own test
 * because "it is the same plumbing" is an argument, and an argument is not an observation.
 */
class DeclaredOperationAccessTest {

    private static final String CONFIG = "src/main/java/com/example/auth/config/SecurityConfig.java";

    @TempDir
    Path projectRoot;

    @Test
    void aDeclaredCommandCanDemandARole() throws IOException {
        project();

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Operation createCustomer nature=COMMAND")
                .contains("access=ROLE admin");
        assertThat(result.tree().orElseThrow().files().get(CONFIG))
                .contains(".requestMatchers(HttpMethod.POST, \"/customers\").hasAnyRole(\"ADMIN\")");
    }

    @Test
    void aDeclaredQueryCanDemandAnIdentity() throws IOException {
        project();

        CompileResult result = compile();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Operation listCustomers nature=QUERY")
                .contains("access=AUTHENTICATED");
        assertThat(result.tree().orElseThrow().files().get(CONFIG))
                .contains(".requestMatchers(HttpMethod.GET, \"/customers\").authenticated()");
    }

    @Test
    void theGeneratedTestRefusesAnAnonymousRequestToEitherOfThem() throws IOException {
        project();

        assertThat(compile().tree().orElseThrow().files()
                        .get("src/test/java/com/example/auth/web/CustomerControllerTest.java"))
                .contains("void listCustomersRefusesAnAnonymousRequest()")
                .contains("void createCustomerRefusesAnAnonymousRequest()");
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## Query List Customers

                ### Endpoint

                GET /customers

                ### Access

                authenticated

                ### Flow

                ```flow
                customers = list Customer
                return customers
                ```

                ### Output

                200 List<Customer>

                ## Command Create Customer

                ### Endpoint

                POST /customers

                ### Access

                role admin

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
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: auth-service
                  group: com.example
                  artifact: auth-service
                  package: com.example.auth

                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.4.4"

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
