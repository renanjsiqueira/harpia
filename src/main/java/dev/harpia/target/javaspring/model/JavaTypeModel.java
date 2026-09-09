package dev.harpia.target.javaspring.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The deliberately small Java declaration model needed by today's Harpia generator. */
public record JavaTypeModel(
        Kind kind,
        String packageName,
        String name,
        JavaVisibility visibility,
        Set<JavaModifier> modifiers,
        Optional<String> documentation,
        List<JavaAnnotationModel> annotations,
        List<JavaImportModel> explicitImports,
        List<String> constants,
        List<JavaTypeRef> superTypes,
        List<JavaFieldModel> fields,
        List<JavaConstructorModel> constructors,
        List<JavaMethodModel> methods,
        Optional<SourceRef> source) {

    public JavaTypeModel {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(visibility, "visibility");
        modifiers = Set.copyOf(modifiers);
        Objects.requireNonNull(documentation, "documentation");
        annotations = List.copyOf(annotations);
        explicitImports = explicitImports.stream().sorted().distinct().toList();
        constants = List.copyOf(constants);
        if (!constants.isEmpty() && kind != Kind.ENUM) {
            throw new IllegalArgumentException("only an enum declares constants: " + name);
        }
        superTypes = List.copyOf(superTypes);
        fields = List.copyOf(fields);
        constructors = List.copyOf(constructors);
        methods = List.copyOf(methods);
        Objects.requireNonNull(source, "source");
    }

    /** A type that declares no constants, which is every kind but an enum. */
    public JavaTypeModel(
            Kind kind,
            String packageName,
            String name,
            JavaVisibility visibility,
            Set<JavaModifier> modifiers,
            Optional<String> documentation,
            List<JavaAnnotationModel> annotations,
            List<JavaImportModel> explicitImports,
            List<JavaTypeRef> superTypes,
            List<JavaFieldModel> fields,
            List<JavaConstructorModel> constructors,
            List<JavaMethodModel> methods,
            Optional<SourceRef> source) {
        this(kind, packageName, name, visibility, modifiers, documentation, annotations,
                explicitImports, List.of(), superTypes, fields, constructors, methods, source);
    }

    public enum Kind {
        CLASS("class"),
        INTERFACE("interface"),
        RECORD("record"),
        ENUM("enum");

        private final String keyword;

        Kind(String keyword) {
            this.keyword = keyword;
        }

        public String keyword() {
            return keyword;
        }
    }
}
