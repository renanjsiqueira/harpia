package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.mapping.SpringValidationMapper;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the request and response models named by the Application IR into Java records.
 *
 * <p>Bean Validation is this target's answer to {@code required} and to the semantic type
 * {@code Email}. The constraint is declared here and enforced where the flow asks for it, so a use
 * case that never says {@code validate input} never rejects a request.
 */
public final class JavaSpringDtoTransformer {

    private final SpringValidationMapper validation = new SpringValidationMapper();

    /**
     * The envelope a paged operation answers with.
     *
     * <p>It is generic and emitted once: what a page reports about itself does not depend on what
     * it holds, so one record serves every paged operation in the project.
     */
    public JavaSourceFile pageResponse(JavaSpringContext context, ApplicationEntity entity) {
        List<JavaFieldModel> components = List.of(
                component("content", JavaTypeRef.parameterized("java.util.List",
                        JavaTypeRef.of("T")), entity),
                component("page", JavaTypeRef.of("int"), entity),
                component("size", JavaTypeRef.of("int"), entity),
                component("totalElements", JavaTypeRef.of("long"), entity),
                component("totalPages", JavaTypeRef.of("int"), entity));
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.RECORD,
                context.layout().packageName(JavaLayout.DTO),
                "PageResponse",
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("One page of results, with what the caller needs to ask for the "
                        + "next one."),
                List.of(),
                List.of(),
                List.of(),
                List.of("T"),
                List.of(),
                components,
                List.of(),
                List.of(),
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.DTO), "PageResponse"),
                type,
                Optional.of(entity.where()));
    }

    private static JavaFieldModel component(
            String name, JavaTypeRef type, ApplicationEntity entity) {
        return new JavaFieldModel(
                name,
                type,
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                List.of(),
                java.util.Optional.empty(),
                java.util.Optional.of(entity.where()));
    }

    public JavaSourceFile response(JavaSpringContext context, ApplicationEntity entity) {
        List<JavaFieldModel> components = entity.fields().stream()
                .map(field -> component(field, List.of(), domain(context)))
                .toList();
        return record(
                context,
                entity.responseTypeName(),
                "Response model generated from the Harpia entity " + entity.typeName() + ".",
                components,
                entity.where());
    }

    public JavaSourceFile request(
            JavaSpringContext context, ApplicationOperation operation, String className) {
        List<JavaFieldModel> components = new ArrayList<>();
        for (ApplicationField field : operation.input()) {
            components.add(component(field, validation.map(field), domain(context)));
        }
        return record(
                context,
                className,
                "Request model generated from the input of " + operation.title() + ".",
                components,
                operation.where());
    }

    private static String domain(JavaSpringContext context) {
        return context.layout().packageName(JavaLayout.DOMAIN);
    }

    private static JavaFieldModel component(
            ApplicationField field,
            List<dev.harpia.target.javaspring.model.JavaAnnotationModel> annotations,
            String domainPackage) {
        return new JavaFieldModel(
                field.name(),
                JavaTypeMapper.map(field.type(), domainPackage),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                annotations,
                Optional.empty(),
                Optional.of(field.where()));
    }

    private static JavaSourceFile record(
            JavaSpringContext context,
            String className,
            String documentation,
            List<JavaFieldModel> components,
            dev.harpia.diag.SourceRef where) {
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.RECORD,
                context.layout().packageName(JavaLayout.DTO),
                className,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of(documentation),
                List.of(),
                List.of(),
                List.of(),
                components,
                List.of(),
                List.of(),
                Optional.of(where));
        return new JavaSourceFile(
                JavaLayout.sourcePath(context.layout().packagePath(JavaLayout.DTO), className),
                type,
                Optional.of(where));
    }
}
