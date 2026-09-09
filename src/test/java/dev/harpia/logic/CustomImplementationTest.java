package dev.harpia.logic;

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
 * The escape hatch: a computation Harpia specifies but does not implement.
 *
 * <p>This boundary is what keeps the grammar small. An algorithm that is recursive, low level or
 * simply specific leaves the language without leaving the product: Harpia still owns the signature,
 * the type checking and the wiring, and owns none of the implementation.
 */
class CustomImplementationTest {

    private static final String CONTRACT =
            "src/main/java/com/example/risk/logic/RiskCalculator.java";
    private static final String GENERATED_LOGIC =
            "src/main/java/com/example/risk/logic/CalculateRisk.java";

    @TempDir
    Path projectRoot;

    @Test
    void aCustomLogicGeneratesAContractAndNoImplementation(@TempDir Path classes)
            throws IOException {
        project("""
                ### Implementation

                custom RiskCalculator
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Logic CalculateRisk custom RiskCalculator");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Computation CalculateRisk -> Decimal custom RiskCalculator");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files)
                .as("Harpia owns the signature; the implementation is the user's")
                .containsKey(CONTRACT)
                .doesNotContainKey(GENERATED_LOGIC);
        assertThat(files.get(CONTRACT))
                .contains("public interface RiskCalculator {")
                .as("an interface method is implicitly public and abstract")
                .contains("    BigDecimal apply(BigDecimal amount, Integer history);")
                .doesNotContain("abstract");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aLogicCannotDeclareBothABodyAndACustomContract() throws IOException {
        project("""
                ### Implementation

                custom RiskCalculator

                ```logic
                return amount
                ```
                """);

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SYNTAX_LOGIC_SECTION);
            assertThat(diagnostic.message())
                    .contains("a custom implementation replaces the body");
        });
    }

    @Test
    void anImplementationMustNameAContract() throws IOException {
        project("""
                ### Implementation

                whatever
                """);

        assertThat(compile().diagnostics()).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.message())
                        .contains("expected 'custom <PascalCaseName>'"));
    }

    @Test
    void aScenarioCannotTargetAnImplementationHarpiaDoesNotOwn() throws IOException {
        project("""
                ### Implementation

                custom RiskCalculator

                ## Scenario Small risk

                ### Given

                - amount: 10
                - history: 1

                ### When

                CalculateRisk

                ### Then

                - result: 0
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_SCENARIO_CUSTOM))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("whose implementation is custom")
                        .contains("test it where it is implemented"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String implementation) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/risk.harpia.md"), """
                # Risk

                ## Logic CalculateRisk

                ### Input

                - amount: Decimal
                - history: Int

                ### Output

                Decimal

                """ + implementation, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: risk-service
                  group: com.example
                  artifact: risk-service
                  package: com.example.risk

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
