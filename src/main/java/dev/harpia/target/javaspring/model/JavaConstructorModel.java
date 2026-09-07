package dev.harpia.target.javaspring.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A Java constructor; it uses the containing type name during rendering. */
public record JavaConstructorModel(
        JavaVisibility visibility,
        List<JavaAnnotationModel> annotations,
        List<JavaParameterModel> parameters,
        List<String> statements,
        Optional<SourceRef> source) {

    public JavaConstructorModel {
        Objects.requireNonNull(visibility, "visibility");
        annotations = List.copyOf(annotations);
        parameters = List.copyOf(parameters);
        statements = List.copyOf(statements);
        Objects.requireNonNull(source, "source");
    }
}
