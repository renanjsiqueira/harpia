package dev.harpia.logic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LogicSpecs;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.Severity;
import dev.harpia.model.LogicModel;
import dev.harpia.model.TypeRef;
import org.junit.jupiter.api.Test;

class LogicAnalyzerTest {

    @Test
    void infersDecimalFromAProductOfDecimalAndDecimalLiteral() {
        LogicModel logic = LogicSpecs.analyze("- total: Decimal", "Decimal", """
                tax = total * 0.10
                return tax""").single();

        assertThat(assignment(logic).type()).isEqualTo(LogicType.scalar(TypeRef.DECIMAL));
    }

    @Test
    void infersBooleanFromAConjunctionOfComparisons() {
        LogicModel logic = LogicSpecs.analyze("""
                - active: Boolean
                - age: Int""", "Boolean", """
                eligible = active and age >= 18
                return eligible""").single();

        assertThat(assignment(logic).type()).isEqualTo(LogicType.scalar(TypeRef.BOOLEAN));
    }

    @Test
    void widensIntTowardsDecimalButNeverNarrows() {
        assertThat(LogicSpecs.analyze("- total: Decimal", "Decimal", """
                bonus = total + 5
                return bonus""").single()).isNotNull();

        assertThat(LogicSpecs.analyze("- total: Decimal", "Int", "return total").codes())
                .containsExactly(ErrorCodes.SEMANTIC_LOGIC_RETURN_TYPE);
    }

    @Test
    void divisionAlwaysProducesDecimalSoIntegerDivisionCannotTruncateSilently() {
        LogicModel logic = LogicSpecs.analyze("""
                - a: Int
                - b: Int""", "Decimal", """
                ratio = a / b
                return ratio""").single();

        assertThat(assignment(logic).type()).isEqualTo(LogicType.scalar(TypeRef.DECIMAL));
    }

    @Test
    void reportsTheOperatorAndBothOperandTypes() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("""
                - name: String
                - total: Decimal""", "Decimal", "return name + total");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SEMANTIC_LOGIC_TYPE);
        assertThat(analysis.messages()).contains("Cannot apply `+` to String and Decimal.");
    }

    @Test
    void plusDoesNotConcatenateText() {
        assertThat(LogicSpecs.analyze("""
                - first: String
                - last: String""", "String", "return first + last").codes())
                .containsExactly(ErrorCodes.SEMANTIC_LOGIC_TYPE);
    }

    @Test
    void theConditionOfIfMustBeBoolean() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- total: Decimal", "Int", """
                if total
                    return 1
                return 0""");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SEMANTIC_LOGIC_TYPE);
        assertThat(analysis.messages()).contains("must be Boolean but was Decimal");
    }

    @Test
    void everyPathMustReturn() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- vip: Boolean", "Int", """
                if vip
                    return 20""");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SEMANTIC_LOGIC_MISSING_RETURN);
        assertThat(analysis.messages()).contains("may finish without returning a value");
    }

    @Test
    void bothBranchesReturningSatisfiesDefiniteReturn() {
        assertThat(LogicSpecs.analyze("- vip: Boolean", "Int", """
                if vip
                    return 20
                else
                    return 0""").diagnostics()).isEmpty();
    }

    @Test
    void aStatementAfterADefiniteReturnIsUnreachable() {
        assertThat(LogicSpecs.analyze("- a: Int", "Int", """
                return a
                b = a""").codes())
                .containsExactly(ErrorCodes.SEMANTIC_LOGIC_UNREACHABLE);
    }

    @Test
    void valuesAreSingleAssignment() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- a: Int", "Int", """
                discount = a
                discount = a + 1
                return discount""");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SEMANTIC_LOGIC_REASSIGNMENT);
        assertThat(analysis.messages()).contains("single assignment");
    }

    @Test
    void aValueDeclaredInsideAConditionalDoesNotEscapeIt() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- vip: Boolean", "Int", """
                if vip
                    rate = 20
                return rate""");

        assertThat(analysis.codes()).contains(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME);
    }

    @Test
    void usingAnUnknownNameNamesTheEnclosingLogic() {
        assertThat(LogicSpecs.analyze("- a: Int", "Int", "return missing").messages())
                .contains("unknown value 'missing'")
                .contains("not a parameter of Logic Sample");
    }

    @Test
    void anUnusedValueIsAWarningAndNotAnError() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- a: Int", "Int", """
                unused = a + 1
                return a""");

        assertThat(analysis.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_LOGIC_UNUSED);
        });
        assertThat(analysis.logics()).hasSize(1);
    }

    @Test
    void resolvesACallToAnotherLogicByNamedArguments() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("""
                # Pricing

                ## Logic Discount

                ### Input

                - total: Decimal

                ### Output

                Decimal

                ```logic
                return total * 0.10
                ```

                ## Logic Sample

                ### Input

                - subtotal: Decimal

                ### Output

                Decimal

                ```logic
                value = Discount(total = subtotal)
                return subtotal - value
                ```
                """);

        assertThat(analysis.diagnostics()).isEmpty();
        assertThat(analysis.logics()).hasSize(2);
    }

    @Test
    void argumentsMustBeNamedComplateAndWellTyped() {
        assertThat(LogicSpecs.analyze("- a: Int", "Int", "return Missing(x = a)").codes())
                .containsExactly(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_FUNCTION);
        assertThat(LogicSpecs.analyze("- a: Int", "Int", "return Sample(a)").messages())
                .contains("must be named");
    }

    @Test
    void aLogicCannotCallItself() {
        LogicSpecs.Analysis analysis =
                LogicSpecs.analyze("- a: Int", "Int", "return Sample(a = a)");

        assertThat(analysis.codes()).contains(ErrorCodes.SEMANTIC_LOGIC_RECURSION);
        assertThat(analysis.messages()).contains("recursion is not supported");
    }

    @Test
    void indirectRecursionIsAlsoRejected() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("""
                # Pricing

                ## Logic First

                ### Input

                - a: Int

                ### Output

                Int

                ```logic
                return Second(a = a)
                ```

                ## Logic Second

                ### Input

                - a: Int

                ### Output

                Int

                ```logic
                return First(a = a)
                ```
                """);

        assertThat(analysis.codes()).contains(ErrorCodes.SEMANTIC_LOGIC_RECURSION);
    }

    @Test
    void duplicateLogicNamesAreRejectedAcrossTheProject() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("""
                # Pricing

                ## Logic Sample

                ### Input

                ### Output

                Int

                ```logic
                return 1
                ```

                ## Logic Sample

                ### Input

                ### Output

                Int

                ```logic
                return 2
                ```
                """);

        assertThat(analysis.codes())
                .as("a duplicate is visible from either declaration")
                .containsExactly(
                        ErrorCodes.SEMANTIC_LOGIC_DUPLICATE, ErrorCodes.SEMANTIC_LOGIC_DUPLICATE);
        assertThat(analysis.diagnostics())
                .allSatisfy(diagnostic -> assertThat(diagnostic.related())
                        .as("the other declaration is a related location, not message text")
                        .singleElement()
                        .satisfies(related -> assertThat(related.where().hasPosition()).isTrue()));
    }

    @Test
    void builtinsAreCheckedAgainstTheRegistry() {
        assertThat(LogicSpecs.analyze("""
                - a: Int
                - b: Int""", "Int", "return min(a, b)").diagnostics()).isEmpty();

        assertThat(LogicSpecs.analyze("- a: Int", "Int", "return round(a)").messages())
                .contains("unknown function 'round'")
                .contains("[max, min]");
    }

    @Test
    void memberAccessIsGrammarValidButSemanticallyUnavailable() {
        LogicSpecs.Analysis analysis =
                LogicSpecs.analyze("- customer: Int", "Int", "return customer.age");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SEMANTIC_LOGIC_UNSUPPORTED);
        assertThat(analysis.messages()).contains("requires a nominal type");
    }

    private static TypedStatement.Assignment assignment(LogicModel logic) {
        return (TypedStatement.Assignment) logic.body().getFirst();
    }
}
