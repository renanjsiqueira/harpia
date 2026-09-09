package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationValue;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.mapping.SpringValidationMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Materialises each declared value as an embeddable class in the domain package.
 *
 * <p>It is a class rather than a record because JPA embeds by field access and needs a no-argument
 * constructor. The all-argument constructor stays, so code that builds one still reads like the
 * value it is.
 */
public final class JavaSpringValueTransformer {

    private final SpringValidationMapper validation = new SpringValidationMapper();

    public List<JavaSourceFile> transform(JavaSpringContext context) {
        return context.application().values().stream()
                .map(declared -> transform(context, declared))
                .toList();
    }

    private JavaSourceFile transform(JavaSpringContext context, ApplicationValue declared) {
        String domain = context.layout().packageName(JavaLayout.DOMAIN);
        List<JavaFieldModel> fields = new ArrayList<>();
        List<JavaParameterModel> parameters = new ArrayList<>();
        List<String> assignments = new ArrayList<>();
        List<JavaMethodModel> methods = new ArrayList<>();
        for (ApplicationField component : declared.components()) {
            JavaTypeRef type = JavaTypeMapper.map(component.type(), domain);
            fields.add(new JavaFieldModel(
                    component.name(),
                    type,
                    JavaVisibility.PRIVATE,
                    Set.of(),
                    validation.map(component),
                    Optional.empty(),
                    Optional.of(component.where())));
            parameters.add(new JavaParameterModel(component.name(), type));
            assignments.add("this." + component.name() + " = " + component.name() + ";");
            methods.add(getter(component, type));
        }

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                domain,
                declared.typeName(),
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("The fields declared for " + declared.typeName()
                        + ", compared by what they hold."),
                List.of(JavaAnnotationModel.marker("jakarta.persistence.Embeddable")),
                List.of(),
                List.of(),
                fields,
                List.of(
                        new JavaConstructorModel(
                                JavaVisibility.PROTECTED,
                                List.of(),
                                List.of(),
                                List.of(),
                                Optional.of(declared.where())),
                        new JavaConstructorModel(
                                JavaVisibility.PUBLIC,
                                List.of(),
                                parameters,
                                assignments,
                                Optional.of(declared.where()))),
                methods,
                Optional.of(declared.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.DOMAIN), declared.typeName()),
                type,
                Optional.of(declared.where()));
    }

    private static JavaMethodModel getter(ApplicationField field, JavaTypeRef type) {
        return new JavaMethodModel(
                JavaLayout.accessor("get", field.name()),
                type,
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(),
                List.of(),
                List.of("return " + field.name() + ";"),
                Optional.of(field.where()));
    }
}
