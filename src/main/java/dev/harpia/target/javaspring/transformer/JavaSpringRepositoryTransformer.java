package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
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
                List.of(),
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.REPOSITORY), className),
                type,
                Optional.of(entity.where()));
    }
}
