package dev.harpia.target.javaspring.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A field after all Harpia-to-Java/Spring decisions have been made. */
public record JavaFieldModel(
        String name,
        JavaTypeRef type,
        JavaVisibility visibility,
        Set<JavaModifier> modifiers,
        List<JavaAnnotationModel> annotations,
        Optional<String> initializer,
        Optional<SourceRef> source) {

    public JavaFieldModel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(visibility, "visibility");
        modifiers = Set.copyOf(modifiers);
        annotations = List.copyOf(annotations);
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(source, "source");
    }
}
