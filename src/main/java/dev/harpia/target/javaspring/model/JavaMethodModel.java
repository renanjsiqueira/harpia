package dev.harpia.target.javaspring.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A Java method with a structured signature and pre-lowered Java statement lines. */
public record JavaMethodModel(
        String name,
        JavaTypeRef returnType,
        JavaVisibility visibility,
        Set<JavaModifier> modifiers,
        List<JavaAnnotationModel> annotations,
        List<JavaParameterModel> parameters,
        List<String> statements,
        List<JavaTypeRef> thrownTypes,
        Optional<String> documentation,
        Optional<SourceRef> source) {

    /** A method that throws nothing and documents nothing, which is the common case. */
    public JavaMethodModel(
            String name,
            JavaTypeRef returnType,
            JavaVisibility visibility,
            Set<JavaModifier> modifiers,
            List<JavaAnnotationModel> annotations,
            List<JavaParameterModel> parameters,
            List<String> statements,
            Optional<SourceRef> source) {
        this(name, returnType, visibility, modifiers, annotations, parameters, statements,
                List.of(), Optional.empty(), source);
    }

    /** A method that throws, keeping the shorter form for the common case. */
    public JavaMethodModel(
            String name,
            JavaTypeRef returnType,
            JavaVisibility visibility,
            Set<JavaModifier> modifiers,
            List<JavaAnnotationModel> annotations,
            List<JavaParameterModel> parameters,
            List<String> statements,
            List<JavaTypeRef> thrownTypes,
            Optional<SourceRef> source) {
        this(name, returnType, visibility, modifiers, annotations, parameters, statements,
                thrownTypes, Optional.empty(), source);
    }

    public JavaMethodModel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(visibility, "visibility");
        modifiers = Set.copyOf(modifiers);
        annotations = List.copyOf(annotations);
        parameters = List.copyOf(parameters);
        statements = List.copyOf(statements);
        thrownTypes = List.copyOf(thrownTypes);
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(source, "source");
        if (statements.stream().anyMatch(line -> line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException("Java statements must be supplied one line at a time");
        }
    }
}
