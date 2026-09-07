package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaModifier;
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
 * Resolves the endpoints declared for one entity into a Spring MVC controller.
 *
 * <p>The controller only binds HTTP: it holds no rule, and the status it returns is the status the
 * specification declared. A body is marked {@code @Valid} exactly when the flow says
 * {@code validate input}, so validation is a declared behaviour rather than a framework default.
 */
public final class JavaSpringControllerTransformer {

    private static final String ANNOTATIONS = "org.springframework.web.bind.annotation.";
    private static final String SERVICE_FIELD = "service";
    private static final String REQUEST_PARAMETER = "request";
    private static final String ID_PARAMETER = "id";

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        JavaLayout layout = context.layout();
        String className = JavaLayout.controllerTypeName(entity.typeName());
        JavaTypeRef serviceType = JavaTypeRef.of(layout.packageName(JavaLayout.SERVICE)
                + "." + JavaLayout.serviceTypeName(entity.typeName()));
        JavaTypeRef responseType = JavaTypeRef.of(
                layout.packageName(JavaLayout.DTO) + "." + entity.responseTypeName());

        List<JavaMethodModel> methods = entity.operations().stream()
                .map(operation -> operation(layout, entity, operation, responseType))
                .toList();

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                layout.packageName(JavaLayout.WEB),
                className,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("HTTP binding generated from the endpoints declared for "
                        + entity.typeName() + "."),
                List.of(JavaAnnotationModel.marker(ANNOTATIONS + "RestController")),
                List.of(),
                List.of(),
                List.of(new JavaFieldModel(
                        SERVICE_FIELD,
                        serviceType,
                        JavaVisibility.PRIVATE,
                        Set.of(JavaModifier.FINAL),
                        List.of(),
                        Optional.empty(),
                        Optional.of(entity.where()))),
                List.of(new JavaConstructorModel(
                        JavaVisibility.PUBLIC,
                        List.of(),
                        List.of(new JavaParameterModel(SERVICE_FIELD, serviceType)),
                        List.of("this." + SERVICE_FIELD + " = " + SERVICE_FIELD + ";"),
                        Optional.of(entity.where()))),
                methods,
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(layout.packagePath(JavaLayout.WEB), className),
                type,
                Optional.of(entity.where()));
    }

    private static JavaMethodModel operation(
            JavaLayout layout,
            ApplicationEntity entity,
            ApplicationOperation operation,
            JavaTypeRef responseType) {
        List<JavaParameterModel> parameters = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        if (operation.endpoint().hasIdPathVariable()) {
            parameters.add(new JavaParameterModel(
                    ID_PARAMETER,
                    JavaTypeMapper.map(entity.idField().type()),
                    List.of(JavaAnnotationModel.marker(ANNOTATIONS + "PathVariable"))));
            arguments.add(ID_PARAMETER);
        }
        operation.requestTypeName().ifPresent(request -> {
            List<JavaAnnotationModel> annotations = new ArrayList<>();
            if (validates(operation)) {
                annotations.add(JavaAnnotationModel.marker("jakarta.validation.Valid"));
            }
            annotations.add(JavaAnnotationModel.marker(ANNOTATIONS + "RequestBody"));
            parameters.add(new JavaParameterModel(
                    REQUEST_PARAMETER,
                    JavaTypeRef.of(layout.packageName(JavaLayout.DTO) + "." + request),
                    annotations));
            arguments.add(REQUEST_PARAMETER);
        });

        String call = SERVICE_FIELD + "." + operation.methodName()
                + "(" + String.join(", ", arguments) + ")";
        int status = operation.result().status();
        List<String> statements = new ArrayList<>();
        if (operation.result().kind() == ApplicationOperation.ResultKind.NOTHING) {
            statements.add(call + ";");
            statements.add("return ResponseEntity.status(" + status + ").build();");
        } else {
            statements.add("return ResponseEntity.status(" + status + ").body(" + call + ");");
        }

        return new JavaMethodModel(
                operation.methodName(),
                JavaTypeRef.parameterized(
                        "org.springframework.http.ResponseEntity", bodyType(operation, responseType)),
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(mapping(operation)),
                parameters,
                statements,
                Optional.of(operation.where()));
    }

    private static JavaTypeRef bodyType(
            ApplicationOperation operation, JavaTypeRef responseType) {
        return switch (operation.result().kind()) {
            case ENTITY -> responseType;
            case LIST -> JavaTypeRef.parameterized("java.util.List", responseType);
            case NOTHING -> JavaTypeRef.of("java.lang.Void");
        };
    }

    private static JavaAnnotationModel mapping(ApplicationOperation operation) {
        String annotation = switch (operation.endpoint().method()) {
            case GET -> "GetMapping";
            case POST -> "PostMapping";
            case PUT -> "PutMapping";
            case DELETE -> "DeleteMapping";
        };
        return JavaAnnotationModel.of(
                ANNOTATIONS + annotation,
                new JavaAnnotationModel.Attribute(
                        "value", "\"" + operation.endpoint().path() + "\""));
    }

    private static boolean validates(ApplicationOperation operation) {
        return operation.flow().stream()
                .anyMatch(instruction -> instruction.command()
                        == ApplicationOperation.FlowCommand.VALIDATE_INPUT);
    }
}
