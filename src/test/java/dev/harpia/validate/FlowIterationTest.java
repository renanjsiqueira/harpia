package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a loop means to the compiler: the item's type, its scope, and the forms left outside. */
class FlowIterationTest {

    private static final String SERVICE =
            "src/main/java/com/example/commerce/service/SalesOrderService.java";

    @TempDir
    Path projectRoot;

    /** C18 — the item's type is the element type of what is being iterated, and it has members. */
    @Test
    void itemMemberTypeComesFromTheDeclaredElement(@TempDir Path classes) throws IOException {
        project("""
                validate input
                order = create SalesOrder from input
                for each line in items
                    running = call AddToTotal(current = order.total, amount = line.unitPrice)
                    set order.total = running
                save order
                return order
                """);

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .as("the Business IR names the item and the type it got")
                .contains("Step ForEach line: OrderItem in items");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction FOR_EACH line in items");
        assertThat(result.tree().orElseThrow().files().get(SERVICE))
                .as("the member is read from the item, with the type the item declares")
                .contains("for (OrderItem line : request.items()) {")
                .contains("AddToTotal.apply(order.getTotal(), line.getUnitPrice());");
        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }

    /** C19 — a member the element does not declare is refused where it is written. */
    @Test
    void invalidMemberAccessIsRejectedBeforeGeneration() throws IOException {
        project("""
                validate input
                order = create SalesOrder from input
                for each line in items
                    running = call AddToTotal(current = order.total, amount = line.priceEach)
                    set order.total = running
                save order
                return order
                """);

        assertThat(errors(ErrorCodes.SEMANTIC_FLOW_MEMBER))
                .as("'priceEach' is not a field of OrderItem")
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("OrderItem has no member 'priceEach'"));
        assertThat(compile().tree()).as("nothing is generated from a flow that does not type").isEmpty();

        project("""
                validate input
                order = create SalesOrder from input
                for each line in items
                    running = call AddToTotal(current = order.total, amount = line.sku)
                    set order.total = running
                save order
                return order
                """);
        assertThat(errors(ErrorCodes.SEMANTIC_LOGIC_TYPE))
                .as("the member resolves and is a String where the call declares a Decimal")
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("must be Decimal but is String"));
    }

    /** C20 — the item and a value the body names are gone after the loop. */
    @Test
    void loopBindingsDoNotEscapeTheirBlock() throws IOException {
        project("""
                validate input
                order = create SalesOrder from input
                for each line in items
                    running = call AddToTotal(current = order.total, amount = line.unitPrice)
                    set order.total = running
                set order.total = running
                save order
                return order
                """);
        assertThat(errors(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME))
                .as("a value the body named does not outlive the body")
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message()).contains("'running'"));

        project("""
                validate input
                order = create SalesOrder from input
                for each line in items
                    running = call AddToTotal(current = order.total, amount = line.unitPrice)
                    set order.total = running
                set order.total = line.unitPrice
                save order
                return order
                """);
        assertThat(errors(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME))
                .as("the item does not outlive the loop either")
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message()).contains("'line'"));
    }

    /** C21 — the six forms this slice leaves outside, each refused with its own reason. */
    @Test
    void unsupportedIterationFormsAreRejected() throws IOException {
        assertRefused("""
                for each line in items
                    for each inner in items
                        set order.total = line.unitPrice
                """, "iterates one level");
        assertRefused("""
                for each line in items
                    break
                """, "'break' is not in this version");
        assertRefused("""
                for each line in items
                    continue
                """, "'continue' is not in this version");
        assertRefused("""
                for each line in items async
                    set order.total = line.unitPrice
                """, "asynchronous iteration");
        assertRefused("""
                for each line in order.items
                    add line to order.items
                """, "adding to 'order.items' while iterating it");
        assertRefused("""
                for each line in order.items
                    remove line from order.items
                """, "removing from 'order.items' while iterating it");
    }

    /** C22 — the arithmetic of a total stays decimal from the specification to the Java. */
    @Test
    void totalExpressionsPreserveDecimalType(@TempDir Path classes) throws IOException {
        project("""
                validate input
                order = create SalesOrder from input
                for each line in items
                    running = call AddToTotal(current = order.total, amount = line.unitPrice)
                    set order.total = running
                save order
                return order
                """);

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("AddToTotal(current: Decimal, amount: Decimal)");
        assertThat(result.tree().orElseThrow().files().get(SERVICE))
                .as("BigDecimal all the way; a double here would lose the cents")
                .contains("BigDecimal running = AddToTotal.apply(")
                .doesNotContain("double")
                .doesNotContain("float");
        assertThat(result.tree().orElseThrow().files()
                        .get("src/main/java/com/example/commerce/logic/AddToTotal.java"))
                .contains("BigDecimal apply(BigDecimal current, BigDecimal amount)");
        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }

    private void assertRefused(String body, String reason) throws IOException {
        project("""
                validate input
                order = create SalesOrder from input
                %s\
                save order
                return order
                """.formatted(body));
        assertThat(compile().diagnostics())
                .as("the Core slice of iteration refuses this, and says why: %s", reason)
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_ITERATION);
                    assertThat(diagnostic.message()).contains(reason);
                });
    }

    private List<Diagnostic> errors(String code) {
        return compile().diagnostics().stream()
                .filter(diagnostic -> diagnostic.code().equals(code))
                .toList();
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String flow) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # SalesOrder

                ## Data

                - id: UUID generated
                - orderNumber: String required unique
                - total: Decimal required default 0
                - items: List<OrderItem> owned required

                ## Logic AddToTotal

                ### Input

                - current: Decimal
                - amount: Decimal

                ### Output

                Decimal

                ```logic
                return current + amount
                ```

                ## Scenario Twenty and thirty

                ### Given

                - current: 20.00
                - amount: 30.00

                ### When

                AddToTotal

                ### Then

                - result: 50.00

                ## Command PlaceOrder

                ### Input

                - orderNumber: String required
                - items: List<OrderItem> required

                ### Flow

                ```flow
                %s```

                ### Output

                201 SalesOrder
                """.formatted(flow), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("specs/item.harpia.md"), """
                # OrderItem

                ## Data

                - id: UUID generated
                - sku: String required
                - quantity: Int required
                - unitPrice: Decimal required

                ## Query GetOrderItem

                ### Flow

                ```flow
                item = load OrderItem by id
                return item
                ```

                ### Output

                200 OrderItem
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
    }
}
