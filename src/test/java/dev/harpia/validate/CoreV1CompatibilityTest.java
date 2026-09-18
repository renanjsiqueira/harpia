package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What {@code languageVersion: 0} still refuses after Core V1 taught the compiler new things.
 *
 * <p>A version number only means something if the older one keeps its meaning. Every construct
 * below is accepted under V1 and refused under V0 by the same fixture — asserting only the refusal
 * would pass for a fixture that is broken for some unrelated reason, which is exactly how a
 * compatibility test rots into a test of nothing.
 *
 * <p>Four more members of this set arrive with the constructs they name: {@code for each} in S2,
 * the declared transaction policy in S3, the binding's failure mapping in S4 and {@code emit} in
 * S5. Each joins this list in the slice that makes it exist.
 */
class CoreV1CompatibilityTest {

    @TempDir
    Path projectRoot;

    /** One construct, the whole specification that uses it, and what V0 answers. */
    record Construct(String name, String spec, String refusal) {
        @Override
        public String toString() {
            return name + " -> " + refusal;
        }
    }

    static Stream<Construct> constructs() {
        return Stream.of(
                new Construct(
                        "call",
                        operation("Place Order", """
                                validate input
                                order = create SalesOrder from input
                                discount = call CalculateDiscount(total = total)
                                save order
                                return order
                                """, ""),
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW),
                new Construct(
                        "if/else",
                        operation("Place Order", """
                                validate input
                                order = create SalesOrder from input
                                if vip
                                    set order.total = 1.00
                                else
                                    set order.total = 2.00
                                save order
                                return order
                                """, ""),
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW),
                new Construct(
                        "require",
                        operation("Place Order", """
                                validate input
                                order = create SalesOrder from input
                                require vip otherwise rejected order
                                save order
                                return order
                                """, ""),
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW),
                new Construct(
                        "fail",
                        operation("Place Order", """
                                validate input
                                order = create SalesOrder from input
                                fail rejected order when total < 0
                                save order
                                return order
                                """, ""),
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW),
                new Construct(
                        "Command heading",
                        operation("Command PlaceOrder", PLAIN, ""),
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW),
                new Construct(
                        "Query heading",
                        // A Query reads, so this one has to read: the point of the case is the
                        // heading, and a Query that created would be refused for creating.
                        reader(),
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW),
                new Construct(
                        "Event",
                        operation("Place Order", PLAIN, """
                                ## Event OrderPlaced

                                ### Payload

                                - total: Decimal
                                """),
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW),
                new Construct(
                        "input the entity does not store",
                        operation("Place Order", PLAIN, "", "- couponCode: String required"),
                        ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constructs")
    void v0RejectsCoreV1OnlyConstructs(Construct construct) throws IOException {
        write(construct.spec(), 1);
        assertThat(compile().diagnostics())
                .as("%s has to be a construct V1 accepts, or the refusal below proves nothing",
                        construct.name())
                .isEmpty();

        write(construct.spec(), 0);
        List<Diagnostic> refused = compile().diagnostics();
        assertThat(refused)
                .as("V0 keeps its meaning: %s is not silently acquired", construct.name())
                .extracting(Diagnostic::code)
                .contains(construct.refusal());
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE));
    }

    /** The same specification with a reading operation, for the cases that are about a Query. */
    private static String reader() {
        return """
                # SalesOrder

                ## Data

                - id: UUID generated
                - orderNumber: String required
                - total: Decimal required default 0
                - vip: Boolean required default false

                ## Query GetOrder

                ### Endpoint

                GET /orders/{id}

                ### Access

                public

                ### Flow

                ```flow
                order = load SalesOrder by id
                return order
                ```

                ### Output

                200 SalesOrder
                """;
    }

    private static final String PLAIN = """
            validate input
            order = create SalesOrder from input
            save order
            return order
            """;

    private static String operation(String heading, String flow, String declaration) {
        return operation(heading, flow, declaration, "");
    }

    /**
     * A whole specification built around one operation.
     *
     * <p>The Logic is always declared, whether or not the flow calls it: a fixture whose shape
     * changes between the accepted and the refused run would be comparing two different things.
     */
    private static String operation(
            String heading, String flow, String declaration, String extraInput) {
        return """
                # SalesOrder

                ## Data

                - id: UUID generated
                - orderNumber: String required
                - total: Decimal required default 0
                - vip: Boolean required default false

                %s
                ## %s

                ### Endpoint

                POST /orders

                ### Access

                public

                ### Input

                - orderNumber: String required
                - total: Decimal required
                - vip: Boolean required
                %s

                ### Flow

                ```flow
                %s```

                ### Output

                201 SalesOrder

                ### Errors

                - rejected order -> 422
                """.formatted(declaration, heading, extraInput, flow);
    }

    private void write(String spec, int languageVersion) {
        try {
            Files.createDirectories(projectRoot.resolve("specs"));
            Files.writeString(
                    projectRoot.resolve("specs/order.harpia.md"), spec, StandardCharsets.UTF_8);
            Files.writeString(projectRoot.resolve("specs/pricing.harpia.md"), """
                    # Pricing

                    ## Logic CalculateDiscount

                    ### Input

                    - total: Decimal

                    ### Output

                    Decimal

                    ```logic
                    return total * 0.10
                    ```

                    ## Scenario Ten percent

                    ### Given

                    - total: 100.00

                    ### When

                    CalculateDiscount

                    ### Then

                    - result: 10.0000
                    """, StandardCharsets.UTF_8);
            Files.writeString(projectRoot.resolve("harpia.yaml"), """
                    harpia:
                      schemaVersion: 1
                      languageVersion: %d

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
                    """.formatted(languageVersion), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
