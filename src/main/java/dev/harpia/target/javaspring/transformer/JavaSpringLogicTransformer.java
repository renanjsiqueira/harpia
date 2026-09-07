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

    public JavaSourceFile transform(JavaSpringContext context, ApplicationLogic logic) {
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
