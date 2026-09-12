package dev.harpia.target.javaspring.renderer;

import dev.harpia.emit.GeneratedFile;
import dev.harpia.emit.GeneratedFileType;
import dev.harpia.emit.GeneratedHeader;
import dev.harpia.emit.GeneratedSourceMapping;
import dev.harpia.emit.OutputNormalizer;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaModifier;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Deterministically renders the small Java Target Model into one compilation unit. */
public final class JavaSourceRenderer {

    private static final String INDENT = "    ";

    public GeneratedFile render(JavaSourceFile file) {
        JavaTypeModel type = file.type();
        StringBuilder source = new StringBuilder();
        List<GeneratedSourceMapping> mappings = new ArrayList<>();
        source.append(GeneratedHeader.javaComment()).append('\n');
        source.append("package ").append(type.packageName()).append(";\n");

        Set<String> imports = JavaImportResolver.resolve(type);
        if (!imports.isEmpty()) {
            source.append('\n');
            imports.forEach(name -> source.append("import ").append(name).append(";\n"));
        }

        source.append('\n');
        int typeStart = nextLine(source);
        type.documentation().ifPresent(documentation -> documentation(source, documentation));
        annotations(source, type.annotations(), "");
        source.append(declaration(type)).append(" {\n");

        boolean wroteMember = false;
        if (!type.constants().isEmpty()) {
            source.append(INDENT).append(String.join(",\n" + INDENT, type.constants()))
                    .append(";\n");
            wroteMember = true;
        }
        // A record declares its state as components, so the same fields render in the header.
        List<JavaFieldModel> bodyFields = type.kind() == JavaTypeModel.Kind.RECORD
                ? List.of()
                : type.fields();
        for (JavaFieldModel field : bodyFields) {
            source.append('\n');
            int start = nextLine(source);
            source.append(renderMember(member -> field(member, field)));
            addMapping(mappings, file.relativePath(), qualified(type) + "#" + field.name(),
                    field.source(), start, nextLine(source));
            wroteMember = true;
        }
        for (JavaConstructorModel constructor : type.constructors()) {
            source.append('\n');
            int start = nextLine(source);
            source.append(renderMember(
                    member -> constructor(member, type.name(), constructor)));
            addMapping(mappings, file.relativePath(), qualified(type) + "#<init>",
                    constructor.source(), start, nextLine(source));
            wroteMember = true;
        }
        for (JavaMethodModel method : type.methods()) {
            source.append('\n');
            int start = nextLine(source);
            source.append(renderMember(member -> method(member, method, type.kind())));
            addMapping(mappings, file.relativePath(), qualified(type) + "#" + method.name(),
                    method.source(), start, nextLine(source));
            wroteMember = true;
        }
        if (wroteMember) {
            source.append('}').append('\n');
        } else {
            source.append('}').append('\n');
        }
        addMapping(mappings, file.relativePath(), qualified(type), type.source(),
                typeStart, nextLine(source));
        return new GeneratedFile(
                file.relativePath(),
                OutputNormalizer.normalize(source.toString()),
                GeneratedFileType.JAVA_SOURCE,
                file.source(),
                mappings);
    }

    private static void addMapping(
            List<GeneratedSourceMapping> mappings,
            String relativePath,
            String symbol,
            Optional<dev.harpia.diag.SourceRef> origin,
            int startLine,
            int endLine) {
        origin.ifPresent(source -> mappings.add(new GeneratedSourceMapping(
                symbol,
                source,
                dev.harpia.diag.SourceRef.span(
                        relativePath, startLine, 1, endLine, 1))));
    }

    /** Normalizing each member first keeps its recorded line range stable after file normalization. */
    private static String renderMember(Consumer<StringBuilder> render) {
        StringBuilder member = new StringBuilder();
        render.accept(member);
        return OutputNormalizer.normalize(member.toString());
    }

    private static int nextLine(StringBuilder source) {
        int line = 1;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String qualified(JavaTypeModel type) {
        return type.packageName() + "." + type.name();
    }

    private static String declaration(JavaTypeModel type) {
        String prefix = keywords(type.visibility(), type.modifiers());
        String components = type.kind() == JavaTypeModel.Kind.RECORD
                ? components(type.fields())
                : "";
        String inheritance = type.superTypes().isEmpty()
                ? ""
                : " extends " + type.superTypes().stream()
                        .map(javaType -> javaType.sourceName())
                        .collect(Collectors.joining(", "));
        return prefix + type.kind().keyword() + " " + type.declaredName() + components
                + inheritance;
    }

    /** Record components, one per line so annotated components stay readable. */
    private static String components(List<JavaFieldModel> fields) {
        if (fields.isEmpty()) {
            return "()";
        }
        return fields.stream()
                .map(field -> {
                    String annotations = field.annotations().stream()
                            .map(JavaSourceRenderer::annotation)
                            .collect(Collectors.joining(" "));
                    return INDENT + INDENT
                            + (annotations.isEmpty() ? "" : annotations + " ")
                            + field.type().sourceName() + " " + field.name();
                })
                .collect(Collectors.joining(",\n", "(\n", ")"));
    }

    private static void field(StringBuilder source, JavaFieldModel field) {
        annotations(source, field.annotations(), INDENT);
        source.append(INDENT)
                .append(keywords(field.visibility(), field.modifiers()))
                .append(field.type().sourceName()).append(' ').append(field.name());
        field.initializer().ifPresent(value -> source.append(" = ").append(value));
        source.append(";\n");
    }

    private static void constructor(
            StringBuilder source, String typeName, JavaConstructorModel constructor) {
        annotations(source, constructor.annotations(), INDENT);
        source.append(INDENT).append(keywords(constructor.visibility(), Set.of()))
                .append(typeName).append('(').append(parameters(constructor.parameters()))
                .append(") {\n");
        statements(source, constructor.statements());
        source.append(INDENT).append("}\n");
    }

    private static void method(
            StringBuilder source, JavaMethodModel method, JavaTypeModel.Kind kind) {
        method.documentation().ifPresent(documentation -> {
            source.append(INDENT).append("/** ").append(documentation).append(" */\n");
        });
        annotations(source, method.annotations(), INDENT);
        // Every method of an interface is public and abstract, so writing either would only
        // repeat the language back to the reader.
        Set<JavaModifier> modifiers = kind == JavaTypeModel.Kind.INTERFACE
                ? method.modifiers().stream()
                        .filter(modifier -> modifier != JavaModifier.ABSTRACT)
                        .collect(java.util.stream.Collectors.toCollection(
                                () -> java.util.EnumSet.noneOf(JavaModifier.class)))
                : method.modifiers();
        source.append(INDENT).append(keywords(method.visibility(), modifiers))
                .append(method.returnType().sourceName()).append(' ').append(method.name())
                .append('(').append(parameters(method.parameters())).append(')')
                .append(thrown(method.thrownTypes()));
        // An abstract method declares a signature, not a body. Emitting braces would be a body
        // that happens to be empty, which is a different method and does not compile here.
        if (method.modifiers().contains(JavaModifier.ABSTRACT)) {
            source.append(";\n");
            return;
        }
        source.append(" {\n");
        statements(source, method.statements());
        source.append(INDENT).append("}\n");
    }

    private static void statements(StringBuilder source, List<String> statements) {
        for (String statement : statements) {
            source.append(INDENT).append(INDENT).append(statement).append('\n');
        }
    }

    private static String thrown(List<JavaTypeRef> thrownTypes) {
        return thrownTypes.isEmpty()
                ? ""
                : " throws " + thrownTypes.stream()
                        .map(JavaTypeRef::sourceName)
                        .collect(Collectors.joining(", "));
    }

    private static String parameters(List<JavaParameterModel> parameters) {
        return parameters.stream().map(parameter -> {
            String annotations = parameter.annotations().stream()
                    .map(JavaSourceRenderer::annotation)
                    .collect(Collectors.joining(" "));
            return (annotations.isEmpty() ? "" : annotations + " ")
                    + parameter.type().sourceName() + " " + parameter.name();
        }).collect(Collectors.joining(", "));
    }

    private static String keywords(JavaVisibility visibility, Set<JavaModifier> modifiers) {
        StringBuilder result = new StringBuilder();
        if (!visibility.keyword().isEmpty()) {
            result.append(visibility.keyword()).append(' ');
        }
        for (JavaModifier modifier : JavaModifier.values()) {
            if (modifiers.contains(modifier)) {
                result.append(modifier.keyword()).append(' ');
            }
        }
        return result.toString();
    }

    private static void annotations(
            StringBuilder source, List<JavaAnnotationModel> annotations, String indent) {
        annotations.forEach(value -> source.append(indent).append(annotation(value)).append('\n'));
    }

    private static String annotation(JavaAnnotationModel annotation) {
        if (annotation.attributes().isEmpty()) {
            return "@" + annotation.type().simpleName();
        }
        // Java allows the shorthand when the only attribute is `value`, and that is what a person
        // writing this annotation by hand would use.
        if (annotation.attributes().size() == 1
                && annotation.attributes().getFirst().name().equals("value")) {
            return "@" + annotation.type().simpleName()
                    + "(" + annotation.attributes().getFirst().value() + ")";
        }
        return "@" + annotation.type().simpleName() + "(" + annotation.attributes().stream()
                .map(attribute -> attribute.name() + " = " + attribute.value())
                .collect(Collectors.joining(", ")) + ")";
    }

    private static void documentation(StringBuilder source, String documentation) {
        source.append("/**\n");
        documentation.lines().forEach(line -> source.append(" * ").append(line).append('\n'));
        source.append(" */\n");
    }
}
