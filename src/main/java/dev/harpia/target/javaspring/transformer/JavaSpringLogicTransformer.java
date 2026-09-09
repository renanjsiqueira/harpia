package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationLogic;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaLogicWriter;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaModifier;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Lowers target-independent Harpia Logic statements into a pure Java class model. */
public final class JavaSpringLogicTransformer {

    public static final String METHOD_NAME = "apply";

    /**
     * The contract of a computation Harpia does not implement.
     *
     * <p>Only the interface is generated. The implementation is the user's, lives outside the
     * generated tree and is therefore never owned, overwritten or cleaned by the compiler — which
     * is the whole point of the escape hatch.
     */
    private static JavaSourceFile contract(JavaSpringContext context, ApplicationLogic logic) {
        String contractName = logic.customContract().orElseThrow();
        // An interface method is implicitly public and abstract. ABSTRACT is kept because the
        // renderer reads it to emit a signature instead of a body; the visibility is left off so
        // the generated source does not repeat what the language already says.
        JavaMethodModel method = new JavaMethodModel(
                METHOD_NAME,
                JavaTypeMapper.map(logic.returnType()),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(JavaModifier.ABSTRACT),
                List.of(),
                logic.parameters().stream()
                        .map(parameter -> new JavaParameterModel(
                                parameter.name(), JavaTypeMapper.map(parameter.type())))
                        .toList(),
                List.of(),
                Optional.of(logic.where()));
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.INTERFACE,
                context.layout().packageName(JavaLayout.LOGIC),
                contractName,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Contract for Harpia Logic " + logic.typeName()
                        + ", implemented outside the generated tree."),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(method),
                Optional.of(logic.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.LOGIC), contractName),
                type,
                Optional.of(logic.where()));
    }

    public JavaSourceFile transform(JavaSpringContext context, ApplicationLogic logic) {
        if (logic.customContract().isPresent()) {
            return contract(context, logic);
        }
        JavaLogicWriter.Result body = JavaLogicWriter.write(logic, METHOD_NAME);
        JavaMethodModel method = new JavaMethodModel(
                METHOD_NAME,
                JavaTypeMapper.map(logic.returnType()),
                JavaVisibility.PUBLIC,
                Set.of(JavaModifier.STATIC),
                List.of(),
                logic.parameters().stream()
                        .map(parameter -> new JavaParameterModel(
                                parameter.name(), JavaTypeMapper.map(parameter.type())))
                        .toList(),
                body.statements(),
                Optional.of(logic.where()));
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                context.layout().packageName(JavaLayout.LOGIC),
                logic.typeName(),
                JavaVisibility.PUBLIC,
                Set.of(JavaModifier.FINAL),
                Optional.of("Pure business computation generated from Harpia Logic "
                        + logic.typeName() + "."),
                List.of(),
                body.imports().stream().map(JavaImportModel::new).toList(),
                List.of(),
                List.of(),
                List.of(new JavaConstructorModel(
                        JavaVisibility.PRIVATE,
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.of(logic.where()))),
                List.of(method),
                Optional.of(logic.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.LOGIC), logic.typeName()),
                type,
                Optional.of(logic.where()));
    }
}
