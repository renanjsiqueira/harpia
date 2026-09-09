package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.capability.Capability;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves that an operation is application behaviour first and an HTTP endpoint only by choice. */
class UnboundOperationTest {

    private static final String SERVICE =
            "src/main/java/com/example/internal/service/CustomerService.java";
    private static final String SERVICE_TEST =
            "src/test/java/com/example/internal/service/CustomerServiceTest.java";
    private static final String CONTROLLER =
            "src/main/java/com/example/internal/web/CustomerController.java";
    private static final String CONTROLLER_TEST =
            "src/test/java/com/example/internal/web/CustomerControllerTest.java";

    @TempDir
    Path projectRoot;

    private CompileResult result;

    @BeforeEach
    void compileProject() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## Command Refresh Customer

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                customer = load Customer by id
                update customer from input
                save customer
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
                  name: internal-customer-service
                  group: com.example
                  artifact: internal-customer-service
                  package: com.example.internal

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
        result = new HarpiaCompiler().compile(new CompileRequest(projectRoot));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void everyIrKeepsTheOperationButNoHttpCapabilityIsInferred() {
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("Command Refresh Customer", "Query List Customers", "Binding none");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Command RefreshCustomer", "Query ListCustomers", "Binding none");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Operation refreshCustomer nature=COMMAND kind=UPDATE "
                        + "transactional=true")
                .contains("Operation listCustomers nature=QUERY kind=LIST transactional=false")
                .contains("Binding none")
                .doesNotContain("Capability http");
        assertThat(result.stages().application().orElseThrow().capabilities()
                        .requires(Capability.HTTP))
                .isFalse();
    }

    @Test
    void javaSpringGeneratesApplicationServicesButNoWebAdapter() {
        SortedMap<String, String> files = result.tree().orElseThrow().files();

        assertThat(files).containsKeys(SERVICE, SERVICE_TEST)
                .doesNotContainKeys(CONTROLLER, CONTROLLER_TEST);
        assertThat(files.get(SERVICE))
                .contains("/** Command Refresh Customer. */")
                .contains("/** Query List Customers. */")
                .contains("@Validated")
                .contains("CustomerResponse refreshCustomer("
                        + "UUID id, @Valid RefreshCustomerRequest request)")
                .contains("List<CustomerResponse> listCustomers()")
                .contains("repository.findById(id)");
        assertThat(files.keySet())
                .contains("src/main/java/com/example/internal/error/NotFoundException.java")
                .doesNotContain("src/main/java/com/example/internal/error/ApiExceptionHandler.java");
        assertThat(files.get("pom.xml")).doesNotContain("spring-boot-starter-web");
    }

    @Test
    void theGeneratedInternalProjectPassesItsOwnOfflineTests(@TempDir Path generated)
            throws Exception {
        write(generated, result.tree().orElseThrow().files());
        Path log = generated.resolve("maven-test.log");
        Process process = new ProcessBuilder("mvn", "-q", "-o", "test")
                .directory(generated.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = Files.exists(log)
                ? Files.readString(log, StandardCharsets.UTF_8)
                : "Maven produced no log";
        assertThat(finished).as("generated Maven build timed out: %s", output).isTrue();
        assertThat(process.exitValue()).as("generated Maven build failed: %s", output).isZero();
    }

    private static void write(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
    }
}
