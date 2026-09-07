package dev.harpia.target.javaspring.transformer;

import dev.harpia.diag.SourceRef;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaModifier;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Lowers the application entry point to a Spring Boot Java class model. */
public final class JavaSpringBootstrapTransformer {

    public JavaSourceFile transform(JavaSpringContext context) {
        JavaLayout layout = context.layout();
        SourceRef source = SourceRef.file("harpia.yaml");
        JavaMethodModel main = new JavaMethodModel(
                "main",
                JavaTypeRef.of("void"),
                JavaVisibility.PUBLIC,
                Set.of(JavaModifier.STATIC),
                List.of(),
                List.of(new JavaParameterModel(
                        "args", JavaTypeRef.of("java.lang.String").array())),
                List.of("SpringApplication.run(" + layout.applicationClassName() + ".class, args);"),
                Optional.of(source));
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                layout.packageName(),
                layout.applicationClassName(),
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.empty(),
                List.of(JavaAnnotationModel.marker(
                        "org.springframework.boot.autoconfigure.SpringBootApplication")),
                List.of(new JavaImportModel("org.springframework.boot.SpringApplication")),
                List.of(),
                List.of(),
                List.of(),
                List.of(main),
                Optional.of(source));
        return new JavaSourceFile(
                JavaLayout.sourcePath(layout.packagePath(), layout.applicationClassName()),
                type,
                Optional.of(source));
    }
}
