package dev.harpia.validate;

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
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An operation whose input is its own contract rather than a projection of a table. */
class ComposedCommandTest {

    private static final String ENTITY =
            "src/main/java/com/example/commerce/domain/SalesOrder.java";
    private static final String REQUEST =
            "src/main/java/com/example/commerce/dto/PlaceOrderRequest.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    /**
     * C14 — a value the operation needs and the entity does not store crosses the compiler whole.
     *
     * <p>The cheap way to accept {@code discountRate} would be to store it: add a column, let the
     * CRUD shape stay true, and the specification now claims the database keeps a number it has no
     * reason to keep. So the assertions are as much about what is absent — no field, no column —
     * as about the value arriving where the flow uses it.
     */
    @Test
    void transientInputAndResultDoNotRequireFakeEntityFields(@TempDir Path classes)
            throws IOException {
        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .as("the input the operation declared, all of it")
                .contains("discountRate");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("discountRate");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(REQUEST))
                .as("the request carries the value the caller has to send")
                .contains("BigDecimal discountRate");
        assertThat(files.get(ENTITY))
                .as("the entity stores what it declared and nothing the operation invented")
                .doesNotContain("discountRate")
                .doesNotContain("discount_rate");
        assertThat(files.get(MIGRATION))
                .as("a value that is not stored has no column")
                .doesNotContain("discount_rate");
        assertThat(files.get("src/main/java/com/example/commerce/service/SalesOrderService.java"))
                .as("the flow reads it from the request and feeds the call with it")
                .contains("CalculateDiscount.apply(request.total(), request.discountRate())")
                .contains("order.setTotal(discount);")
                .doesNotContain("setDiscountRate");
        GeneratedJava.compiles(files, classes);
    }

    private CompileResult compile() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # SalesOrder

                ## Data

                - id: UUID generated
                - orderNumber: String required
                - total: Decimal required default 0

                ## Logic CalculateDiscount

                ### Input

                - total: Decimal
                - rate: Decimal

                ### Output

                Decimal

                ```logic
                return total * rate
                ```

                ## Scenario A tenth of a hundred

                ### Given

                - total: 100.00
                - rate: 0.10

                ### When

                CalculateDiscount

                ### Then

                - result: 10.0000

                ## Command PlaceOrder

                ### Input

                - orderNumber: String required
                - total: Decimal required
                - discountRate: Decimal required

                ### Flow

                ```flow
                validate input
                order = create SalesOrder from input
                discount = call CalculateDiscount(total = total, rate = discountRate)
                set order.total = discount
                save order
                return order
                ```

                ### Output

                201 SalesOrder
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: commerce-service
                  group: com.example
                  artifact: commerce-service
                  package: com.example.commerce

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
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }
}
