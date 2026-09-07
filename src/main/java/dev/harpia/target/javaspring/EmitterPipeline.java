package dev.harpia.target.javaspring;

import dev.harpia.emit.GeneratedTree;
import dev.harpia.target.TargetGenerationResult;
import dev.harpia.target.javaspring.renderer.JavaSourceRenderer;
import dev.harpia.target.javaspring.transformer.JavaSpringProjectTransformer;
import java.util.List;
import java.util.Objects;

/** Runs the Java/Spring emitters in a fixed order and returns a complete deterministic tree. */
public final class EmitterPipeline {

    private final List<Emitter> emitters;

    public EmitterPipeline(List<Emitter> emitters) {
        this.emitters = List.copyOf(emitters);
    }

    public static EmitterPipeline standard() {
        JavaSpringTemplates templates = new JavaSpringTemplates();
        return new EmitterPipeline(List.of(
                new PomEmitter(templates),
                new AppConfigEmitter(templates),
                new MigrationEmitter(templates),
                new JavaSourceEmitter(
                        new JavaSpringProjectTransformer(), new JavaSourceRenderer())));
    }

    public TargetGenerationResult emit(JavaSpringContext context) {
        Objects.requireNonNull(context, "context");
        GeneratedTree output = new GeneratedTree();
        for (Emitter emitter : emitters) {
            emitter.emit(context, output);
        }
        return new TargetGenerationResult(output.generatedFiles());
    }
}
