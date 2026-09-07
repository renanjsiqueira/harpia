package dev.harpia.target.javaspring.model;

import java.util.List;
import java.util.Objects;

/** A resolved Java annotation. Attribute values are already target syntax, never Harpia flags. */
public record JavaAnnotationModel(
        JavaTypeRef type,
        List<Attribute> attributes) {

    public JavaAnnotationModel {
        Objects.requireNonNull(type, "type");
        attributes = List.copyOf(attributes);
    }

    public static JavaAnnotationModel marker(String canonicalName) {
        return new JavaAnnotationModel(JavaTypeRef.of(canonicalName), List.of());
    }

    public static JavaAnnotationModel of(String canonicalName, Attribute... attributes) {
        return new JavaAnnotationModel(JavaTypeRef.of(canonicalName), List.of(attributes));
    }

    public record Attribute(String name, String value) {
        public Attribute {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            if (name.isBlank() || value.isBlank()) {
                throw new IllegalArgumentException("annotation attributes must not be blank");
            }
        }
    }
}
