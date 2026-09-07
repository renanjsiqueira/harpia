package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaDefaultValueMapper;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.mapping.SpringPersistenceMapper;
import dev.harpia.target.javaspring.mapping.SpringValidationMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Translates a persistent Application IR entity into a complete JPA Java model. */
public final class JavaSpringEntityTransformer {

    private final SpringValidationMapper validation = new SpringValidationMapper();
    private final SpringPersistenceMapper persistence = new SpringPersistenceMapper();
    private final JavaDefaultValueMapper defaults = new JavaDefaultValueMapper();

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        List<JavaFieldModel> fields = new ArrayList<>();
        List<JavaMethodModel> methods = new ArrayList<>();
        List<JavaImportModel> explicitImports = new ArrayList<>();
        for (ApplicationField field : entity.fields()) {
            List<JavaAnnotationModel> annotations = new ArrayList<>(validation.map(field));
            annotations.addAll(persistence.fieldAnnotations(entity, field));
            explicitImports.addAll(persistence.additionalImports(entity, field));
            JavaTypeRef type = JavaTypeMapper.map(field.type());
            fields.add(new JavaFieldModel(
                    field.name(),
                    type,
                    JavaVisibility.PRIVATE,
                    Set.of(),
                    annotations,
                    defaults.map(field),
                    Optional.of(field.where())));
            methods.add(getter(field, type));
            methods.add(setter(field, type));
        }

        String packageName = context.layout().packageName(JavaLayout.DOMAIN);
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                packageName,
                entity.typeName(),
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Persistent entity generated from the Harpia entity "
                        + entity.typeName() + "."),
                persistence.entityAnnotations(entity),
                explicitImports,
                List.of(),
                fields,
                // Public rather than protected: JPA accepts either, and the generated service
                // instantiates the entity from another package for `create ... from input`.
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.of(entity.where()))),
                methods,
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(context.layout().packagePath(JavaLayout.DOMAIN), entity.typeName()),
                type,
                Optional.of(entity.where()));
    }

    private static JavaMethodModel getter(ApplicationField field, JavaTypeRef type) {
        return new JavaMethodModel(
                accessor("get", field.name()),
                type,
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(),
                List.of(),
                List.of("return " + field.name() + ";"),
                Optional.of(field.where()));
    }

    private static JavaMethodModel setter(ApplicationField field, JavaTypeRef type) {
        return new JavaMethodModel(
                accessor("set", field.name()),
                JavaTypeRef.of("void"),
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(),
                List.of(new JavaParameterModel(field.name(), type)),
                List.of("this." + field.name() + " = " + field.name() + ";"),
                Optional.of(field.where()));
    }

    private static String accessor(String prefix, String fieldName) {
        return prefix + fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1);
    }
}
