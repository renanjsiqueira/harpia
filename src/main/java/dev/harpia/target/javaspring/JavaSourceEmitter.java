package dev.harpia.target.javaspring;

import dev.harpia.emit.GeneratedTree;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.target.TargetGenerationException;
import dev.harpia.target.javaspring.model.JavaProjectModel;
import dev.harpia.target.javaspring.renderer.JavaSourceRenderer;
import dev.harpia.target.javaspring.transformer.JavaSpringProjectTransformer;
import java.util.Objects;

/** Connects target lowering to Java rendering without exposing either concern to compiler core. */
public final class JavaSourceEmitter implements Emitter {

    private final JavaSpringProjectTransformer transformer;
    private final JavaSourceRenderer renderer;

    public JavaSourceEmitter(
            JavaSpringProjectTransformer transformer, JavaSourceRenderer renderer) {
        this.transformer = Objects.requireNonNull(transformer, "transformer");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public void emit(JavaSpringContext context, GeneratedTree output) {
        JavaProjectModel project;
        try {
            project = transformer.transform(context);
        } catch (TargetGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TargetGenerationException(
                    ErrorCodes.TARGET_TRANSFORMATION_FAILURE,
                    "Java/Spring target failed to transform the application model",
                    SourceRef.file("harpia.yaml"),
                    exception);
        }
        for (var source : project.sourceFiles()) {
            try {
                output.put(renderer.render(source));
            } catch (TargetGenerationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new TargetGenerationException(
                        ErrorCodes.TARGET_TEMPLATE_FAILURE,
                        "Java/Spring target failed to render `" + source.relativePath() + "`",
                        source.source().orElse(SourceRef.file("harpia.yaml")),
                        exception);
            }
        }
    }
}
