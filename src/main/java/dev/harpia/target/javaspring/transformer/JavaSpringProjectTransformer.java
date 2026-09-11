package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.application.ApplicationLogic;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.model.JavaProjectModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The target lowering boundary: Application IR in, structured Java project model out. */
public final class JavaSpringProjectTransformer {

    private final JavaSpringBootstrapTransformer bootstrap = new JavaSpringBootstrapTransformer();
    private final JavaSpringEnumTransformer enums = new JavaSpringEnumTransformer();
    private final JavaSpringValueTransformer values = new JavaSpringValueTransformer();
    private final JavaSpringEventTransformer events = new JavaSpringEventTransformer();
    private final JavaSpringEntityTransformer entities = new JavaSpringEntityTransformer();
    private final JavaSpringRepositoryTransformer repositories =
            new JavaSpringRepositoryTransformer();
    private final JavaSpringLogicTransformer logics = new JavaSpringLogicTransformer();
    private final JavaSpringIntegrationClientTransformer integrations =
            new JavaSpringIntegrationClientTransformer();
    private final JavaSpringDtoTransformer dtos = new JavaSpringDtoTransformer();
    private final JavaSpringServiceTransformer services = new JavaSpringServiceTransformer();
    private final JavaSpringControllerTransformer controllers =
            new JavaSpringControllerTransformer();
    private final JavaSpringErrorTransformer errors = new JavaSpringErrorTransformer();
    private final JavaSpringServiceTestTransformer serviceTests =
            new JavaSpringServiceTestTransformer();
    private final JavaSpringControllerTestTransformer controllerTests =
            new JavaSpringControllerTestTransformer();
    private final JavaSpringLogicTestTransformer logicTests = new JavaSpringLogicTestTransformer();

    public JavaProjectModel transform(JavaSpringContext context) {
        Objects.requireNonNull(context, "context");
        List<JavaSourceFile> files = new ArrayList<>();
        java.util.Optional<dev.harpia.target.javaspring.model.JavaSourceFile> pageResponse =
                java.util.Optional.empty();
        files.add(bootstrap.transform(context));
        files.addAll(enums.transform(context));
        files.addAll(values.transform(context));
        files.addAll(events.transform(context));
        files.addAll(integrations.transform(context));
        for (ApplicationEntity entity : context.application().entities()) {
            boolean hasHttpBindings = entity.operations().stream()
                    .anyMatch(operation -> operation.endpoint().isPresent());
            files.add(entities.transform(context, entity));
            files.add(repositories.transform(context, entity));
            files.add(dtos.response(context, entity));
            // One envelope serves every paged operation, so it is emitted once.
            if (pageResponse.isEmpty() && entity.operations().stream().anyMatch(operation ->
                    operation.result().kind() == ApplicationOperation.ResultKind.PAGE)) {
                pageResponse = java.util.Optional.of(dtos.pageResponse(context, entity));
            }
            for (ApplicationOperation operation : entity.operations()) {
                operation.requestTypeName().ifPresent(request ->
                        files.add(dtos.request(context, operation, request)));
            }
            files.add(services.transform(context, entity));
            if (hasHttpBindings) {
                files.add(controllers.transform(context, entity));
            }
            if (context.application().settings().generation().tests()
                    && !entity.operations().isEmpty()) {
                files.add(serviceTests.transform(context, entity));
            }
            if (context.application().settings().generation().tests() && hasHttpBindings) {
                files.add(controllerTests.transform(context, entity));
            }
        }
        files.addAll(errors.transform(context));
        if (context.application().settings().generation().tests()) {
            files.addAll(logicTests.transform(context));
        }
        for (ApplicationLogic logic : context.application().logics()) {
            files.add(logics.transform(context, logic));
        }
        pageResponse.ifPresent(files::add);
        return new JavaProjectModel(files);
    }
}
