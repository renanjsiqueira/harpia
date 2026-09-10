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

/** First-class outbound ports before a provider decides how to call them. */
class IntegrationDeclarationTest {

    @TempDir
    Path projectRoot;

    @Test
    void integrationAndOperationsCrossEveryTargetIndependentStage() throws IOException {
        config(1);
        integration("fraud", "Commerce", """
                ## Integration FraudService

                External fraud analysis used during checkout.

                ### Operation CheckOrder

                #### Input

                - orderId: UUID required

                #### Output

                FraudResult

                #### Errors

                - RateLimited
                - ServiceUnavailable

                ### Operation CheckCustomer

                #### Output

                Boolean

                ## Value FraudResult

                - approved: Boolean required
                - score: Decimal required
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("Integration FraudService")
                .contains("Operation CheckOrder")
                .contains("Input orderId: UUID required")
                .contains("Output FraudResult")
                .contains("Error RateLimited")
                .contains("Operation CheckCustomer");
        assertThat(Inspector.render(result, Stage.SYMBOLS).orElseThrow())
                .contains("Namespace integrations")
                .contains("CheckOrder(orderId: UUID required) -> FraudResult")
                .contains("throws RateLimited|ServiceUnavailable")
                .contains("CheckCustomer() -> Boolean");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Integration FraudService")
                .contains("Port CheckOrder")
                .contains("Input orderId: UUID required")
                .contains("Output FraudResult")
                .contains("Error ServiceUnavailable");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("OutboundPort FraudService")
                .contains("Parameter orderId: UUID required")
                .contains("Result FraudResult")
                .contains("Failure RateLimited")
                .contains("Operation CheckCustomer");
    }

    @Test
    void duplicateOperationNamesAreRejectedInsideTheirPort() throws IOException {
        config(1);
        integration("fraud", "Commerce", """
                ## Integration FraudService

                ### Operation CheckOrder

                #### Output

                nothing

                ### Operation CheckOrder
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_INTEGRATION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("repeats operation 'CheckOrder'"));
    }

    @Test
    void duplicateIntegrationsAreReportedAcrossModules() throws IOException {
        config(1);
        integration("fraud-a", "Commerce", """
                ## Integration FraudService

                ### Operation CheckOrder

                #### Output

                Boolean
                """);
        integration("fraud-b", "Risk", """
                ## Integration FraudService

                ### Operation CheckCustomer

                #### Output

                Boolean
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_DUPLICATE_INTEGRATION))
                .hasSize(2)
                .allSatisfy(diagnostic -> assertThat(diagnostic.related()).singleElement());
    }

    @Test
    void anIntegrationMustDeclareAtLeastOneOperation() throws IOException {
        config(1);
        integration("fraud", "Commerce", "## Integration FraudService\n");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_INTEGRATION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("declares no operations"));
    }

    @Test
    void versionZeroDoesNotInterpretIntegrationAsALegacyUseCase() throws IOException {
        config(0);
        integration("fraud", "Commerce", """
                ## Integration FraudService

                ### Operation CheckOrder
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SYNTAX_DECLARATION_TOO_NEW))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("declares an Integration")
                        .contains("languageVersion 1"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void integration(String file, String module, String body) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/" + file + ".harpia.md"),
                "# " + module + "\n\n" + body, StandardCharsets.UTF_8);
    }

    private void config(int languageVersion) throws IOException {
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
    }
}
