package dev.harpia.target.javaspring.model;

import java.util.List;
import java.util.Objects;

/** A Java method or constructor parameter. */
public record JavaParameterModel(
        String name,
        JavaTypeRef type,
        List<JavaAnnotationModel> annotations) {

    public JavaParameterModel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        annotations = List.copyOf(annotations);
    }

    public JavaParameterModel(String name, JavaTypeRef type) {
        this(name, type, List.of());
    }
}
