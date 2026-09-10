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
 * Assigning one field from an expression.
 *
 * <p>{@code update ... from input} copies whatever the input carries. {@code set} computes a single
 * value, which is why both exist: they are different operations, and the flow says which one
 * happened.
 */
class SetFieldTest {

    private static final String SERVICE =
            "src/main/java/com/example/invoice/service/InvoiceService.java";

    @TempDir
    Path projectRoot;

    @Test
    void aSetAssignsTheFieldFromATypedExpression(@TempDir Path classes) throws IOException {
        project("set invoice.total = subtotal + fee");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction SET_FIELD total = subtotal + fee");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .as("Decimal addition is the same one a Logic body would render")
                .contains("invoice.setTotal(request.subtotal().add(request.fee()));");
        assertThat(files.get(SERVICE).indexOf("setTotal"))
                .as("assigned before the save, or the computed value is not what is stored")
                .isLessThan(files.get(SERVICE).indexOf("repository.save"));

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aSetMustProduceTheFieldsType() throws IOException {
        project("set invoice.total = subtotal > fee");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_LOGIC_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("must be Decimal but is Boolean"));
    }

    @Test
    void aSetCannotAssignAGeneratedField() throws IOException {
        project("set invoice.id = subtotal + fee");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .isNotEmpty()
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("'id' is generated, so the flow cannot assign it"));
    }

    @Test
    void aSetCannotNameAFieldTheEntityDoesNotHave() throws IOException {
        project("set invoice.discount = subtotal + fee");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .isNotEmpty()
                .anySatisfy(diagnostic ->
                        assertThat(diagnostic.message()).contains("has no field 'discount'"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String setLine) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/invoice.harpia.md"), """
                # Invoice

                ## Data

                - id: UUID generated
                - subtotal: Decimal required
                - fee: Decimal required
                - total: Decimal required

                ## Create Invoice

                ### Endpoint

                POST /invoices

                ### Access

                public

                ### Input

                - subtotal: Decimal required
                - fee: Decimal required

                ### Flow

                ```flow
                validate input
                invoice = create Invoice from input
                """ + setLine + """

                save invoice
                return invoice
                ```

                ### Output

                201 Invoice
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: invoice-service
                  group: com.example
                  artifact: invoice-service
                  package: com.example.invoice

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
