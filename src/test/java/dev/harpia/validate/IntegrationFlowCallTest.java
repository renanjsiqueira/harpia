package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Calls to outbound ports are resolved before any transport provider is chosen. */
class IntegrationFlowCallTest {

    @TempDir
    Path projectRoot;

    @Test
    void aTypedIntegrationCallCrossesBothIrLayersInSignatureOrder() throws IOException {
        project("""
                approved = call FraudService.CheckOrder(
                    context = context,
                    orderId = orderId
                )
                purchase = create Purchase from input
                save purchase
                return purchase
                """);

        CompileResult result = compile();

        assertThat(result.diagnostics())
                .as(result.diagnostics().toString())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.TARGET_CONSTRUCT_UNSUPPORTED))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("has no HTTP binding"));
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Step IntegrationCall approved = FraudService.CheckOrder("
                        + "orderId: UUID, context: FraudContext) -> Boolean");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction CALL_INTEGRATION approved = FraudService.CheckOrder("
                        + "orderId: UUID, context: FraudContext) -> Boolean");
        assertThat(result.tree()).isEmpty();
    }

    @Test
    void anOperationReturningNothingIsCalledWithoutAssignment() throws IOException {
        project("""
                call FraudService.Notify(orderId = orderId)
                purchase = create Purchase from input
                save purchase
                return purchase
                """);

        CompileResult result = compile();

        assertThat(result.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_FLOW_CALL_RESULT))
                .isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("FraudService.Notify(orderId: UUID) -> nothing");
    }

    @Test
    void theOperationMustExistOnTheNamedIntegration() throws IOException {
        project("""
                approved = call FraudService.Missing(orderId = orderId)
                purchase = create Purchase from input
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_FLOW_CALL_TARGET))
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.message())
                            .contains("Integration 'FraudService' has no operation 'Missing'");
                    assertThat(diagnostic.related()).singleElement();
                });
    }

    @Test
    void resultPresenceMustMatchThePortContract() throws IOException {
        project("""
                call FraudService.CheckOrder(orderId = orderId, context = context)
                ignored = call FraudService.Notify(orderId = orderId)
                purchase = create Purchase from input
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_FLOW_CALL_RESULT))
                .hasSize(2)
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("CheckOrder").contains("assign it"))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("Notify").contains("returns nothing"));
    }

    @Test
    void argumentsAreCheckedAgainstNominalAndScalarPortTypes() throws IOException {
        project("""
                approved = call FraudService.CheckOrder(
                    orderId = context,
                    context = orderId
                )
                purchase = create Purchase from input
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_LOGIC_TYPE))
                .hasSize(2)
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("must be UUID but is FraudContext"))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("must be FraudContext but is UUID"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String flow) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # Purchase

                ## Value FraudContext

                - fingerprint: String required

                ## Integration FraudService

                ### Operation CheckOrder

                #### Input

                - orderId: UUID required
                - context: FraudContext required

                #### Output

                Boolean

                #### Errors

                - Unavailable

                ### Operation Notify

                #### Input

                - orderId: UUID required

                #### Output

                nothing

                ## Data

                - id: UUID generated
                - orderId: UUID required
                - context: FraudContext required

                ## Command CreateOrder

                ### Input

                - orderId: UUID required
                - context: FraudContext required

                ### Flow

                ```flow
                %s```

                ### Output

                201 Purchase
                """.formatted(flow), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: order-service
                  group: com.example
                  artifact: order-service
                  package: com.example.order

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
