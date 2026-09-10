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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Typed values crossing an outbound port without choosing a transport provider. */
class IntegrationContractTest {

    @TempDir
    Path projectRoot;

    @BeforeEach
    void configure() throws IOException {
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

    @Test
    void valueCollectionsAndNothingAreValidPortResults() throws IOException {
        spec("fraud", """
                # Fraud

                ## Value FraudResult

                - approved: Boolean required

                ## Integration FraudService

                ### Operation CheckMany

                #### Input

                - customerIds: List<UUID> required

                #### Output

                List<FraudResult>

                ### Operation Notify

                #### Input

                - result: FraudResult required

                #### Output

                nothing
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Parameter customerIds: List<UUID> required")
                .contains("Result List<FraudResult>")
                .contains("Parameter result: FraudResult required")
                .contains("Result nothing");
    }

    @Test
    void everyOperationDeclaresItsSuccessfulResult() throws IOException {
        spec("fraud", """
                # Fraud

                ## Integration FraudService

                ### Operation CheckOrder

                #### Input

                - orderId: UUID required
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_INTEGRATION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("missing '#### Output'"));
    }

    @Test
    void operationContractsRejectUnknownAndRepeatedSections() throws IOException {
        spec("fraud", """
                # Fraud

                ## Integration FraudService

                ### Operation CheckOrder

                #### Transport

                HTTP

                #### Output

                Boolean

                #### Output

                nothing
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_INTEGRATION))
                .hasSize(2)
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("unknown operation subsection '#### Transport'"))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("repeats '#### Output'"));
    }

    @Test
    void duplicateInputsCarryTheFirstDeclarationLocation() throws IOException {
        spec("fraud", """
                # Fraud

                ## Integration FraudService

                ### Operation CheckOrder

                #### Input

                - orderId: UUID required
                - orderId: UUID required

                #### Output

                Boolean
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_DUPLICATE_INTEGRATION_INPUT))
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.message()).contains("repeats input 'orderId'");
                    assertThat(diagnostic.related()).singleElement();
                });
    }

    @Test
    void contractTypesMustResolveToValuesRatherThanPersistenceObjects() throws IOException {
        spec("fraud", """
                # Fraud

                ## Data

                - id: UUID generated

                ## Integration FraudService

                ### Operation CheckOrder

                #### Input

                - order: Fraud required
                - unknown: MissingValue required

                #### Output

                Reference<Fraud>
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_INTEGRATION_TYPE))
                .hasSize(2)
                .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("carries persistence identity"));
        assertThat(result.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_UNKNOWN_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("MissingValue"));
    }

    @Test
    void errorsAreNamedFailureVariantsAndCannotRepeat() throws IOException {
        spec("fraud", """
                # Fraud

                ## Integration FraudService

                ### Operation CheckOrder

                #### Output

                Boolean

                #### Errors

                - RateLimited
                - RateLimited
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_INTEGRATION))
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.message()).contains("repeats error 'RateLimited'");
                    assertThat(diagnostic.related()).singleElement();
                });
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void spec(String name, String contents) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(
                projectRoot.resolve("specs/" + name + ".harpia.md"),
                contents,
                StandardCharsets.UTF_8);
    }
}
