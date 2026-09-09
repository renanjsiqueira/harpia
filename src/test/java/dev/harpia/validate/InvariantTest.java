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
 * A condition about what the entity is allowed to be.
 *
 * <p>A rule constrains what one operation was asked to do, so it is checked where that operation
 * validates its input. An invariant constrains the entity itself, so it holds whichever operation
 * ran — and the moment that matters is the one where the entity becomes durable.
 */
class InvariantTest {

    private static final String SERVICE =
            "src/main/java/com/example/billing/service/InvoiceService.java";
    private static final String VIOLATION =
            "src/main/java/com/example/billing/error/InvariantViolationException.java";
    private static final String HANDLER =
            "src/main/java/com/example/billing/error/ApiExceptionHandler.java";

    @TempDir
    Path projectRoot;

    @Test
    void anInvariantIsCheckedBeforeTheEntityBecomesDurable(@TempDir Path classes)
            throws IOException {
        project("""
                ## Invariants

                - discount <= total
                - total > 0
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Invariant discount <= total")
                .contains("Invariant total > 0");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        String service = files.get(SERVICE);
        assertThat(service)
                .as("the invariant reads the entity, not the request that happened to build it")
                .contains("if (!(invoice.getDiscount().compareTo(invoice.getTotal()) <= 0)) {")
                .contains("throw new InvariantViolationException(\"Invoice\", "
                        + "\"discount <= total\");");
        assertThat(service.indexOf("InvariantViolationException"))
                .as("checked before the save, because after it the bad state is already stored")
                .isLessThan(service.indexOf("repository.save"));
        assertThat(files).containsKey(VIOLATION);
        assertThat(files.get(HANDLER))
                .as("a well-formed request asking for a forbidden state is not invalid input")
                .contains("@ExceptionHandler(InvariantViolationException.class)")
                .contains("ResponseEntity.status(422)");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void anInvariantSeesTheEntityAndNotTheOperationInput() throws IOException {
        project("""
                ## Invariants

                - discount <= paid
                """);

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME);
            assertThat(diagnostic.message()).contains("unknown value 'paid'");
        });
    }

    @Test
    void anInvariantThatIsNotBooleanIsRefused() throws IOException {
        project("""
                ## Invariants

                - total - discount
                """);

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.message())
                        .contains("invariant 'total - discount' must be Boolean but is Decimal"));
    }

    @Test
    void invariantsNeedAnEntityToConstrain() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/invoice.harpia.md"), """
                # Invoice

                ## Invariants

                - 1 > 0
                """, StandardCharsets.UTF_8);
        config();

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INVARIANT_WITHOUT_ENTITY))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("needs a '## Data' in the same module"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String invariants) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/invoice.harpia.md"), """
                # Invoice

                ## Data

                - id: UUID generated
                - total: Decimal required
                - discount: Decimal required

                """ + invariants + """

                ## Create Invoice

                ### Endpoint

                POST /invoices

                ### Access

                public

                ### Input

                - total: Decimal required
                - discount: Decimal required

                ### Flow

                ```flow
                validate input
                invoice = create Invoice from input
                save invoice
                return invoice
                ```

                ### Output

                201 Invoice
                """, StandardCharsets.UTF_8);
        config();
    }

    private void config() throws IOException {
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: billing-service
                  group: com.example
                  artifact: billing-service
                  package: com.example.billing

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
