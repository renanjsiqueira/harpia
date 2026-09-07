package dev.harpia.inspect;

import dev.harpia.CompileResult;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders one stage of a compilation that already happened.
 *
 * <p>It reads the models the compiler carried on its result rather than recomputing them, so what
 * is shown is what was compiled.
 */
public final class Inspector {

    private Inspector() {
    }

    public static Optional<String> render(CompileResult result, Stage stage) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(stage, "stage");
        CompileResult.Stages stages = result.stages();
        return switch (stage) {
            case AST -> stages.modules().isEmpty()
                    ? Optional.empty()
                    : Optional.of(AstRenderer.render(stages.modules()));
            case SYMBOLS -> stages.business().map(SymbolRenderer::render);
            case BUSINESS_IR -> stages.business().map(BusinessIrRenderer::render);
            case APPLICATION_IR -> stages.application().map(ApplicationIrRenderer::render);
        };
    }
}
