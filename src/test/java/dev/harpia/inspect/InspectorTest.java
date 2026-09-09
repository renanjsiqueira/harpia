package dev.harpia.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Inspection must show what the compiler produced, deterministically, and must stop at the stage a
 * failed compilation actually reached.
 */
class InspectorTest {

    @ParameterizedTest
    @EnumSource(Stage.class)
    void everyStageRendersDeterministically(Stage stage) {
        assertThat(Inspector.render(compile("examples/customer"), stage))
                .isEqualTo(Inspector.render(compile("examples/customer"), stage));
    }

    @Test
    void aProjectWithoutEntitiesStillShowsItsComputations() {
        CompileResult result = compile("examples/business-logic/pricing");

        assertThat(Inspector.render(result, Stage.SYMBOLS)).hasValueSatisfying(symbols ->
                assertThat(symbols)
                        .contains("Namespace types\n  (empty)")
                        .contains("Namespace computations\n  CalculateDiscount"));
        assertThat(Inspector.render(result, Stage.BUSINESS_IR)).hasValueSatisfying(business ->
                assertThat(business).contains("Logic CalculateDiscount"));
    }

    @Test
    void scenariosAppearAsSymbolsBecauseTheyAreDeclaredNames() {
        assertThat(Inspector.render(compile("examples/business-logic/pricing"), Stage.SYMBOLS))
                .hasValueSatisfying(symbols -> assertThat(symbols)
                        .contains("VIP discount -> CalculateDiscount"));
    }

    @Test
    void theSyntaxTreeShowsWhatWasWrittenBeforeAnyNameIsResolved() {
        assertThat(Inspector.render(compile("examples/customer"), Stage.AST))
                .hasValueSatisfying(ast -> assertThat(ast)
                        .startsWith("Project languageVersion=0\n")
                        .contains("Module Customer (specs/customer.harpia.md)")
                        .contains("  UseCase Create Customer")
                        .contains("    Flow ValidateInput"));
    }

    @Test
    void aStageTheCompilationNeverReachedIsNotInvented() {
        CompileResult failed = new CompileResult(
                Optional.empty(),
                CompileResult.Stages.none(),
                java.util.List.of());

        for (Stage stage : Stage.values()) {
            assertThat(Inspector.render(failed, stage))
                    .as("%s must not be fabricated", stage.id())
                    .isEmpty();
        }
    }

    @Test
    void stageIdentifiersAreStableAndResolvable() {
        assertThat(Stage.find("business-ir")).contains(Stage.BUSINESS_IR);
        assertThat(Stage.find("BUSINESS-IR")).contains(Stage.BUSINESS_IR);
        assertThat(Stage.find("nonsense")).isEmpty();
        assertThat(Stage.ids()).isEqualTo("ast, symbols, business-ir, application-ir");
    }

    private static CompileResult compile(String project) {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of(project), CompileRequest.Mode.VALIDATE));
        assertThat(result.hasErrors()).isFalse();
        return result;
    }
}
