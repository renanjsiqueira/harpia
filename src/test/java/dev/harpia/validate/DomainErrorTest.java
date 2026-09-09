package dev.harpia.validate;

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
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An error the business names, rather than one the runtime detects.
 *
 * <p>{@code invalid input}, {@code not found} and {@code duplicate} have fixed statuses because the
 * compiler knows exactly when each occurs. A domain error is different: only the specification
 * knows what it means, so it declares both the name and the status, and both have to survive into
 * the generated contract.
 */
class DomainErrorTest {

    private static final String EXCEPTION =
            "src/main/java/com/example/customer/error/InsufficientBalanceException.java";
    private static final String HANDLER =
            "src/main/java/com/example/customer/error/ApiExceptionHandler.java";

    @TempDir
    Path projectRoot;

    @Test
    void aNamedDomainErrorBecomesATypeAndAStatus(@TempDir Path classes) throws IOException {
        project("""
                ### Errors

                - invalid input -> 400
                - insufficient balance -> 422
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Failure DOMAIN 'insufficient balance' -> 422");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files).containsKey(EXCEPTION);
        assertThat(files.get(EXCEPTION))
                .contains("public class InsufficientBalanceException extends RuntimeException")
                .contains("public InsufficientBalanceException(String message)");
        assertThat(files.get(HANDLER))
                .contains("@ExceptionHandler(InsufficientBalanceException.class)")
                .contains("ResponseEntity.status(422)")
                .contains("new ApiError(422, \"insufficient balance\", exception.getMessage())");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aStatusThatIsNotAFailureIsRefused() throws IOException {
        project("""
                ### Errors

                - insufficient balance -> 200
                """);

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SYNTAX_ERROR_CONDITION);
            assertThat(diagnostic.message()).contains("an error status is 4xx or 5xx");
        });
    }

    @Test
    void aDetectedConditionKeepsItsFixedStatus() throws IOException {
        project("""
                ### Errors

                - not found -> 410
                """);

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.message())
                        .contains("a detected condition maps to 404"));
    }

    @Test
    void theSameDomainErrorCannotAnswerTwoStatuses() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## Create Customer

                ### Endpoint

                POST /customers

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                customer = create Customer from input
                save customer
                return customer
                ```

                ### Output

                201 Customer

                ### Errors

                - insufficient balance -> 422

                ## Update Customer

                ### Endpoint

                PUT /customers/{id}

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                customer = load Customer by id
                update customer from input
                save customer
                return customer
                ```

                ### Output

                200 Customer

                ### Errors

                - insufficient balance -> 409
                """, StandardCharsets.UTF_8);
        config();

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_ERROR_STATUS_CONFLICT);
            assertThat(diagnostic.message())
                    .contains("'insufficient balance' maps to 409 here and to 422 elsewhere");
            assertThat(diagnostic.related()).isNotEmpty();
        });
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String errors) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## Create Customer

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

                """ + errors, StandardCharsets.UTF_8);
        config();
    }

    private void config() throws IOException {
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

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """, StandardCharsets.UTF_8);
    }
}
