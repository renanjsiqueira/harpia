package dev.harpia.logic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LogicSpecs;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.model.LogicModel;
import dev.harpia.model.TypeRef;
import org.junit.jupiter.api.Test;

class LogicGrammarTest {

    @Test
    void parsesTheDeclarationWithItsInputOutputAndBlock() {
        LogicModel logic = LogicSpecs.analyze("""
                - total: Decimal
                - vip: Boolean""", "Decimal", "return total").single();

        assertThat(logic.name()).isEqualTo("Sample");
        assertThat(logic.parameters())
                .extracting(LogicModel.Parameter::name)
                .containsExactly("total", "vip");
        assertThat(logic.returnType()).isEqualTo(LogicType.scalar(TypeRef.DECIMAL));
        assertThat(logic.body()).singleElement().isInstanceOf(TypedStatement.Return.class);
    }

    @Test
    void multiplicationBindsTighterThanAddition() {
        LogicModel logic = LogicSpecs.analyze(
                "- total: Decimal", "Decimal", "return total + total * total").single();

        TypedStatement.Return returned = (TypedStatement.Return) logic.body().getFirst();
        TypedExpression.Binary sum = (TypedExpression.Binary) returned.value();
        assertThat(sum.operator()).isEqualTo(BinaryOperator.ADD);
        assertThat(sum.right()).isInstanceOfSatisfying(TypedExpression.Binary.class, product ->
                assertThat(product.operator()).isEqualTo(BinaryOperator.MULTIPLY));
    }

    @Test
    void parenthesesOverridePrecedence() {
        LogicModel logic = LogicSpecs.analyze(
                "- total: Decimal", "Decimal", "return (total + total) * total").single();

        TypedStatement.Return returned = (TypedStatement.Return) logic.body().getFirst();
        assertThat(returned.value())
                .isInstanceOfSatisfying(TypedExpression.Binary.class, product ->
                        assertThat(product.operator()).isEqualTo(BinaryOperator.MULTIPLY));
    }

    @Test
    void andBindsTighterThanOr() {
        LogicModel logic = LogicSpecs.analyze("""
                - a: Boolean
                - b: Boolean
                - c: Boolean""", "Boolean", "return a or b and c").single();

        TypedStatement.Return returned = (TypedStatement.Return) logic.body().getFirst();
        TypedExpression.Binary disjunction = (TypedExpression.Binary) returned.value();
        assertThat(disjunction.operator()).isEqualTo(BinaryOperator.OR);
        assertThat(disjunction.right()).isInstanceOfSatisfying(
                TypedExpression.Binary.class,
                conjunction -> assertThat(conjunction.operator()).isEqualTo(BinaryOperator.AND));
    }

    @Test
    void comparisonIsNotAssociative() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("""
                - a: Int
                - b: Int
                - c: Int""", "Boolean", "return a < b < c");

        assertThat(analysis.codes()).contains(ErrorCodes.SYNTAX_LOGIC_EXPRESSION);
        assertThat(analysis.messages()).contains("comparison is not associative");
    }

    @Test
    void rejectsJavaBooleanOperatorsAndPointsToTheHarpiaWords() {
        assertThat(LogicSpecs.analyze("- a: Boolean", "Boolean", "return a && a").messages())
                .contains("use 'and' or 'or'");
        assertThat(LogicSpecs.analyze("- a: Boolean", "Boolean", "return !a").messages())
                .contains("'!' is not Harpia Logic; use 'not'");
    }

    @Test
    void reservesPercentForThePercentageLiteralInsteadOfModulo() {
        LogicSpecs.Analysis analysis =
                LogicSpecs.analyze("- a: Int", "Int", "return a % 2");

        assertThat(analysis.codes()).contains(ErrorCodes.SYNTAX_LOGIC_TOKEN);
        assertThat(analysis.messages()).contains("reserved for the Percentage literal");
    }

    @Test
    void indentationMustBeFourSpaces() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- a: Boolean", "Int", """
                if a
                  return 1
                return 0""");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SYNTAX_LOGIC_INDENT);
        assertThat(analysis.messages()).contains("multiple of four spaces");
    }

    @Test
    void rejectsTabs() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- a: Boolean", "Int", """
                if a
                \treturn 1
                return 0""");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SYNTAX_LOGIC_INDENT);
        assertThat(analysis.messages()).contains("tabs are not allowed");
    }

    @Test
    void parsesIfWithElse() {
        LogicModel logic = LogicSpecs.analyze("- vip: Boolean", "Int", """
                if vip
                    return 20
                else
                    return 0""").single();

        assertThat(logic.body()).singleElement()
                .isInstanceOfSatisfying(TypedStatement.Conditional.class, conditional -> {
                    assertThat(conditional.thenBranch()).hasSize(1);
                    assertThat(conditional.elseBranch()).isPresent();
                });
    }

    @Test
    void rejectsElseWithoutIf() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- a: Boolean", "Int", """
                return 0
                else
                    return 1""");

        assertThat(analysis.messages()).contains("'else' has no matching 'if'");
    }

    @Test
    void aSideEffectingOperationInsideLogicExplainsTheBoundary() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- a: Int", "Int", """
                save customer
                return a""");

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SEMANTIC_LOGIC_SIDE_EFFECT);
        assertThat(analysis.messages())
                .contains("operation 'save' is not allowed inside Logic")
                .contains("Logic must be pure")
                .contains("move side-effecting operations to Flow");
    }

    @Test
    void wordsHeldForPlannedFeaturesNeverBecomeIdentifiers() {
        assertThat(LogicSpecs.analyze("- a: Int", "Int", "return when").messages())
                .contains("reserved for a planned Harpia Logic feature");
    }

    @Test
    void aLogicDeclarationRequiresExactlyOneLogicBlock() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("""
                # Pricing

                ## Logic Sample

                ### Input

                ### Output

                Int
                """);

        assertThat(analysis.codes()).containsExactly(ErrorCodes.SYNTAX_LOGIC_SECTION);
        assertThat(analysis.messages()).contains("exactly one fenced code block named 'logic'");
    }

    @Test
    void aModuleMayDeclareLogicWithoutAnyEntity() {
        LogicSpecs.Analysis analysis = LogicSpecs.analyze("- a: Int", "Int", "return a");

        assertThat(analysis.diagnostics()).isEmpty();
        assertThat(analysis.specification().orElseThrow().declaresEntity()).isFalse();
        assertThat(analysis.specification().orElseThrow().logics()).hasSize(1);
    }
}
