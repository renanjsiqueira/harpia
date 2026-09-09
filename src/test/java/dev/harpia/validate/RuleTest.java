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
 * A rule the compiler enforces, rather than a paragraph it ignores.
 *
 * <p>Bean Validation covers what a field can say about itself. A rule is the part of validating the
 * input that relates two values, which no field annotation can express — so it is a condition the
 * compiler types and the target checks, in the place the flow says validation happens.
 */
class RuleTest {

    private static final String SERVICE =
            "src/main/java/com/example/billing/service/InvoiceService.java";
    private static final String VIOLATION =
            "src/main/java/com/example/billing/error/RuleViolationException.java";
    private static final String HANDLER =
            "src/main/java/com/example/billing/error/ApiExceptionHandler.java";

    @TempDir
    Path projectRoot;

    @Test
    void aRuleIsTypedCarriedAndCheckedWhereTheFlowValidates(@TempDir Path classes)
            throws IOException {
        project("""
                ### Rules

                Prose stays documentation.

                - total > 0
                - discount <= total
                """, "validate input");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Rule total > 0")
                .contains("Rule discount <= total");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .as("Decimal comparison is the same one a Logic body would render")
                .contains("if (!(request.total().compareTo(new BigDecimal(\"0\")) > 0)) {")
                .contains("throw new RuleViolationException(\"total > 0\");")
                .contains("if (!(request.discount().compareTo(request.total()) <= 0)) {");
        assertThat(files).containsKey(VIOLATION);
        assertThat(files.get(HANDLER))
                .as("a rule is invalid input, so it answers the status declared for it")
                .contains("@ExceptionHandler(RuleViolationException.class)")
                .contains("ResponseEntity.status(400)");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aRuleThatIsNotBooleanIsRefused() throws IOException {
        project("""
                ### Rules

                - total + discount
                """, "validate input");

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_LOGIC_TYPE);
            assertThat(diagnostic.message())
                    .contains("rule 'total + discount' must be Boolean but is Decimal");
        });
    }

    @Test
    void aRuleCannotNameSomethingOutsideTheInput() throws IOException {
        project("""
                ### Rules

                - total > paid
                """, "validate input");

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME);
            assertThat(diagnostic.message()).contains("unknown value 'paid'");
        });
    }

    @Test
    void aRuleThatTheFlowNeverValidatesIsRefused() throws IOException {
        project("""
                ### Rules

                - total > 0
                """, "");

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_RULE_WITHOUT_INPUT);
            assertThat(diagnostic.message()).contains("never validates input");
        });
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String rules, String validate) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/invoice.harpia.md"), """
                # Invoice

                ## Data

                - id: UUID generated
                - total: Decimal required
                - discount: Decimal required

                ## Create Invoice

                ### Endpoint

                POST /invoices

                ### Access

                public

                ### Input

                - total: Decimal required
                - discount: Decimal required

                """ + rules + """

                ### Flow

                ```flow
                """ + (validate.isEmpty() ? "" : validate + "\n") + """
                invoice = create Invoice from input
                save invoice
                return invoice
                ```

                ### Output

                201 Invoice

                ### Errors

                - invalid input -> 400
                """, StandardCharsets.UTF_8);
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
