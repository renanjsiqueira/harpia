package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEnum;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Materialises each declared enum as a Java enum in the domain package.
 *
 * <p>The constant keeps the value's Harpia spelling upper-cased, so what is stored and what the
 * specification wrote stay legible as the same thing.
 */
public final class JavaSpringEnumTransformer {

    public List<JavaSourceFile> transform(JavaSpringContext context) {
        return context.application().enums().stream()
                .map(declared -> transform(context, declared))
                .toList();
    }

    private static JavaSourceFile transform(
            JavaSpringContext context, ApplicationEnum declared) {
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.ENUM,
                context.layout().packageName(JavaLayout.DOMAIN),
                declared.typeName(),
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("The values declared for " + declared.typeName() + "."),
                List.of(),
                List.of(),
                declared.values().stream().map(JavaTypeMapper::enumConstant).toList(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.of(declared.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.DOMAIN), declared.typeName()),
                type,
                Optional.of(declared.where()));
    }
}
