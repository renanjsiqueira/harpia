package dev.harpia.target.javaspring.renderer;

import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import java.util.SortedSet;
import java.util.TreeSet;

/** Resolves imports from the structured model, never by scanning rendered source with regexes. */
public final class JavaImportResolver {

    private JavaImportResolver() {
    }

    public static SortedSet<String> resolve(JavaTypeModel type) {
        TreeSet<String> imports = new TreeSet<>();
        type.explicitImports().stream().map(JavaImportModel::canonicalName).forEach(imports::add);
        annotations(type.annotations(), imports);
        types(type.superTypes(), imports);
        type.fields().forEach(field -> {
            add(field.type(), imports);
            annotations(field.annotations(), imports);
        });
        type.constructors().forEach(constructor -> constructor(constructor, imports));
        type.methods().forEach(method -> method(method, imports));
        imports.removeIf(name -> name.startsWith("java.lang.")
                || packageName(name).equals(type.packageName()));
        return java.util.Collections.unmodifiableSortedSet(imports);
    }

    private static void constructor(JavaConstructorModel constructor, TreeSet<String> imports) {
        annotations(constructor.annotations(), imports);
        constructor.parameters().forEach(parameter -> parameter(parameter, imports));
    }

    private static void method(JavaMethodModel method, TreeSet<String> imports) {
        add(method.returnType(), imports);
        types(method.thrownTypes(), imports);
        annotations(method.annotations(), imports);
        method.parameters().forEach(parameter -> parameter(parameter, imports));
    }

    private static void parameter(JavaParameterModel parameter, TreeSet<String> imports) {
        add(parameter.type(), imports);
        annotations(parameter.annotations(), imports);
    }

    private static void annotations(
            java.util.List<JavaAnnotationModel> annotations, TreeSet<String> imports) {
        annotations.forEach(annotation -> add(annotation.type(), imports));
    }

    private static void types(java.util.List<JavaTypeRef> types, TreeSet<String> imports) {
        types.forEach(type -> add(type, imports));
    }

    private static void add(JavaTypeRef type, TreeSet<String> imports) {
        type.flattened()
                .map(JavaTypeRef::canonicalName)
                .filter(name -> name.contains("."))
                .forEach(imports::add);
    }

    private static String packageName(String canonicalName) {
        int separator = canonicalName.lastIndexOf('.');
        return separator < 0 ? "" : canonicalName.substring(0, separator);
    }
}
