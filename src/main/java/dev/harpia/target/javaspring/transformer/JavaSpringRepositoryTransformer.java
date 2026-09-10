package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaModifier;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.application.ApplicationField;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Resolves a persistence port as a Spring Data repository interface. */
public final class JavaSpringRepositoryTransformer {

    /**
     * One derived finder per field the flows search by.
     *
     * <p>Spring Data reads the method name, so the name is the query. The field is unique, which is
     * what lets the result be an {@code Optional} of one rather than a list of maybe-many.
     */
    private static List<JavaMethodModel> finders(
            JavaSpringContext context, ApplicationEntity entity) {
        String domain = context.layout().packageName(JavaLayout.DOMAIN);
        java.util.TreeMap<String, JavaMethodModel> byName = new java.util.TreeMap<>();
        for (ApplicationOperation operation : entity.operations()) {
            for (ApplicationOperation.FlowInstruction instruction : operation.flow()) {
                if (instruction.command() != ApplicationOperation.FlowCommand.FIND_BY) {
                    continue;
                }
                String fieldName = instruction.field().orElseThrow();
                ApplicationField field = entity.fields().stream()
                        .filter(candidate -> candidate.name().equals(fieldName))
                        .findFirst()
                        .orElseThrow();
                String method = "findBy" + JavaLayout.accessor("", fieldName);
                byName.putIfAbsent(method, new JavaMethodModel(
                        method,
                        JavaTypeRef.parameterized(
                                "java.util.Optional",
                                JavaTypeRef.of(domain + "." + entity.typeName())),
                        JavaVisibility.PACKAGE_PRIVATE,
                        Set.of(JavaModifier.ABSTRACT),
                        List.of(),
                        List.of(new JavaParameterModel(
                                fieldName, JavaTypeMapper.stored(field.type(), domain))),
                        List.of(),
                        Optional.of(instruction.where())));
            }
        }
        return List.copyOf(byName.values());
    }

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        String className = JavaLayout.repositoryTypeName(entity.typeName());
        JavaTypeRef domainType = JavaTypeRef.of(
                context.layout().packageName(JavaLayout.DOMAIN) + "." + entity.typeName());
        JavaTypeRef repository = JavaTypeRef.parameterized(
                "org.springframework.data.jpa.repository.JpaRepository",
                domainType,
                JavaTypeMapper.map(entity.idField().scalarType()));
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.INTERFACE,
                context.layout().packageName(JavaLayout.REPOSITORY),
                className,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Persistence port generated from the Harpia entity "
                        + entity.typeName() + "."),
                List.of(),
                List.of(),
                List.of(repository),
                List.of(),
                List.of(),
                finders(context, entity),
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.REPOSITORY), className),
                type,
                Optional.of(entity.where()));
    }
}
