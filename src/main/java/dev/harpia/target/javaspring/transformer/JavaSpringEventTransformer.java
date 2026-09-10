package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEvent;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Materialises each declared event as an immutable record in the event package.
 *
 * <p>A record, because an event is a fact: it happened, and nothing that happens can later be
 * edited. Nothing here publishes or subscribes — the contract exists whether or not a provider
 * ever carries it anywhere.
 */
public final class JavaSpringEventTransformer {

    public List<JavaSourceFile> transform(JavaSpringContext context) {
        return context.application().events().stream()
                .map(event -> transform(context, event))
                .toList();
    }

    private JavaSourceFile transform(JavaSpringContext context, ApplicationEvent event) {
        String domain = context.layout().packageName(JavaLayout.DOMAIN);
        List<JavaFieldModel> components = event.payload().stream()
                .map(field -> new JavaFieldModel(
                        field.name(),
                        JavaTypeMapper.map(field.type(), domain),
                        JavaVisibility.PACKAGE_PRIVATE,
                        Set.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(field.where())))
                .toList();

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.RECORD,
                context.layout().packageName(JavaLayout.EVENT),
                event.name(),
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("The payload announced by " + event.name() + "."),
                List.of(),
                List.of(),
                List.of(),
                components,
                List.of(),
                List.of(),
                Optional.of(event.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.EVENT), event.name()),
                type,
                Optional.of(event.where()));
    }
}
