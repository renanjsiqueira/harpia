package dev.harpia.target.javaspring.mapping;

import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationScalarType;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves Harpia validation semantics into Jakarta Validation annotations. */
public final class SpringValidationMapper {

    public List<JavaAnnotationModel> map(ApplicationField field) {
        Objects.requireNonNull(field, "field");
        List<JavaAnnotationModel> annotations = new ArrayList<>();
        if (field.valueType().isPresent()) {
            // The value validates its own fields, so the owner asks for that to happen.
            annotations.add(JavaAnnotationModel.marker("jakarta.validation.Valid"));
            if (field.required()) {
                annotations.add(
                        JavaAnnotationModel.marker("jakarta.validation.constraints.NotNull"));
            }
            return List.copyOf(annotations);
        }
        if (field.enumTypeName().isPresent()) {
            // A declared type constrains itself: the values it admits are the only ones that
            // parse. What remains to say is whether it may be absent.
            if (field.required() && !field.generated()) {
                annotations.add(
                        JavaAnnotationModel.marker("jakarta.validation.constraints.NotNull"));
            }
            return List.copyOf(annotations);
        }
        if (field.scalarType() == ApplicationScalarType.EMAIL) {
            annotations.add(JavaAnnotationModel.marker("jakarta.validation.constraints.Email"));
        }
        if (field.required() && !field.generated()) {
            annotations.add(JavaAnnotationModel.marker(isText(field.scalarType())
                    ? "jakarta.validation.constraints.NotBlank"
                    : "jakarta.validation.constraints.NotNull"));
        }
        return List.copyOf(annotations);
    }

    private static boolean isText(ApplicationScalarType type) {
        return type == ApplicationScalarType.STRING
                || type == ApplicationScalarType.TEXT
                || type == ApplicationScalarType.EMAIL;
    }
}
