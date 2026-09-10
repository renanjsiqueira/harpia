package dev.harpia.logic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LogicSpecs;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.Severity;
import dev.harpia.model.ScenarioModel;
import org.junit.jupiter.api.Test;

/**
 * A scenario is the only construct that can say what a pure computation should return, so most of
 * what matters here is refusing to accept one that would generate a meaningless test.
 */
class ScenarioTest {

    @Test
    void bindsEveryInputAndTheExpectedResult() {
        ScenarioModel scenario = LogicSpecs.analyze(sample("""
                ## Scenario VIP discount

                ### Given

                - total: 100
                - vip: true

                ### When

                Sample

                ### Then

                - result: 20.00
                """)).scenarios().getFirst();

        assertThat(scenario.title()).isEqualTo("VIP discount");
        assertThat(scenario.methodName()).isEqualTo("vipDiscount");
        assertThat(scenario.computation()).isEqualTo("Sample");
        assertThat(scenario.arguments())
                .extracting(ScenarioModel.Binding::name)
                .as("arguments follow the declared input order, not the written order")
                .containsExactly("total", "vip");
        assertThat(scenario.expected().source()).isEqualTo("20.00");
    }

    @Test
    void namesAnUnknownComputation() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Missing target

                ### Given

                - total: 100

                ### When

                NotDeclared

                ### Then

                - result: 1
                """)).codes())
                .contains(ErrorCodes.SEMANTIC_SCENARIO_UNKNOWN_TARGET);
    }

    @Test
    void mustGiveEveryInputExactlyOnce() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Incomplete

                ### Given

                - total: 100

                ### When

                Sample

                ### Then

                - result: 1
                """)).messages())
                .contains("does not give input 'vip'");

        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Unknown input

                ### Given

                - total: 100
                - vip: true
                - missing: 1

                ### When

                Sample

                ### Then

                - result: 1
                """)).messages())
                .contains("has no input 'missing'");
    }

    @Test
    void refusesAValueThatDoesNotBelongToTheDeclaredType() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Wrong type

                ### Given

                - total: 100
                - vip: "yes"

                ### When

                Sample

                ### Then

                - result: 1
                """)).messages())
                .contains("is not a Boolean");
    }

    @Test
    void refusesAResultThatDoesNotBelongToTheOutputType() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Wrong result

                ### Given

                - total: 100
                - vip: true

                ### When

                Sample

                ### Then

                - result: true
                """)).messages())
                .contains("result 'true' is not a Decimal");
    }

    @Test
    void anIntegerWidensToADecimalExactlyLikeAnArgumentInsideALogic() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Whole number

                ### Given

                - total: 100
                - vip: true

                ### When

                Sample

                ### Then

                - result: 20
                """)).diagnostics())
                .isEmpty();
    }

    @Test
    void refusesTwoScenariosWithTheSameTitle() {
        String scenario = """
                ## Scenario Same name

                ### Given

                - total: 100
                - vip: true

                ### When

                Sample

                ### Then

                - result: 1
                """;
        assertThat(LogicSpecs.analyze(sample(scenario + "\n" + scenario)).codes())
                .contains(ErrorCodes.SEMANTIC_SCENARIO_DUPLICATE);
    }

    @Test
    void requiresGivenWhenAndThen() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario No result

                ### Given

                - total: 100
                - vip: true

                ### When

                Sample
                """)).messages())
                .contains("is missing '### Then'");
    }

    @Test
    void thenDeclaresExactlyOneResult() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Two results

                ### Given

                - total: 100
                - vip: true

                ### When

                Sample

                ### Then

                - result: 1
                - other: 2
                """)).messages())
                .contains("must contain exactly one '- result: <value>'");
    }

    @Test
    void aTitleBecomesAStableJavaMethodName() {
        assertThat(LogicSpecs.analyze(sample("""
                ## Scenario Small order gets nothing

                ### Given

                - total: 100
                - vip: false

                ### When

                Sample

                ### Then

                - result: 0
                """)).scenarios().getFirst().methodName())
                .isEqualTo("smallOrderGetsNothing");
    }

    @Test
    void aLogicWithoutAnyScenarioIsReportedRatherThanSilentlyUntested() {
        dev.harpia.diag.DiagnosticCollector diagnostics =
                new dev.harpia.diag.DiagnosticCollector();
        LogicSpecs.Analysis analysis = LogicSpecs.analyze(
                "- total: Decimal\n- vip: Boolean", "Decimal", "return total");

        dev.harpia.validate.TestCoverage.report(
                new dev.harpia.model.ProjectModel(
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        analysis.logics(),
                        java.util.List.of()),
                true,
                diagnostics);

        assertThat(diagnostics.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_SCENARIO_MISSING);
            assertThat(diagnostic.message()).contains("declares no scenario");
            assertThat(diagnostic.hint()).hasValueSatisfying(hint ->
                    assertThat(hint).contains("## Scenario"));
        });
    }

    @Test
    void noWarningIsRaisedWhenTestsWereNotRequested() {
        dev.harpia.diag.DiagnosticCollector diagnostics =
                new dev.harpia.diag.DiagnosticCollector();
        LogicSpecs.Analysis analysis = LogicSpecs.analyze(
                "- total: Decimal\n- vip: Boolean", "Decimal", "return total");

        dev.harpia.validate.TestCoverage.report(
                new dev.harpia.model.ProjectModel(
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        analysis.logics(),
                        java.util.List.of()),
                false,
                diagnostics);

        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    private static String sample(String scenario) {
        return """
                # Pricing

                ## Logic Sample

                ### Input

                - total: Decimal
                - vip: Boolean

                ### Output

                Decimal

                ```logic
                if vip
                    return total * 0.20

                return 0
                ```

                """ + scenario;
    }
}
