package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import dev.harpia.target.javaspring.mapping.SpringValidationMapper;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaConstructorModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
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
import java.util.TreeSet;

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
    private static final SpringValidationMapper VALIDATION = new SpringValidationMapper();

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        JavaLayout layout = context.layout();
        String className = JavaLayout.controllerTypeName(entity.typeName());
        JavaTypeRef serviceType = JavaTypeRef.of(layout.packageName(JavaLayout.SERVICE)
                + "." + JavaLayout.serviceTypeName(entity.typeName()));
        JavaTypeRef responseType = JavaTypeRef.of(
                layout.packageName(JavaLayout.DTO) + "." + entity.responseTypeName());

        TreeSet<String> explicitImports = new TreeSet<>();
        List<JavaMethodModel> methods = new ArrayList<>();
        for (ApplicationOperation operation : entity.operations()) {
            if (operation.endpoint().isPresent()) {
                methods.add(operation(layout, entity, operation, responseType, explicitImports));
            }
        }

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                layout.packageName(JavaLayout.WEB),
                className,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("HTTP binding generated from the endpoints declared for "
                        + entity.typeName() + "."),
                controllerAnnotations(methods),
                explicitImports.stream().map(JavaImportModel::new).toList(),
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

    /**
     * Spring only enforces constraints on individual parameters when the class asks it to, so
     * {@code @Validated} appears exactly when some parameter carries one.
     */
    private static List<JavaAnnotationModel> controllerAnnotations(List<JavaMethodModel> methods) {
        List<JavaAnnotationModel> annotations = new ArrayList<>();
        annotations.add(JavaAnnotationModel.marker(ANNOTATIONS + "RestController"));
        boolean constrained = methods.stream()
                .flatMap(method -> method.parameters().stream())
                .flatMap(parameter -> parameter.annotations().stream())
                .anyMatch(annotation -> annotation.type().canonicalName()
                        .startsWith("jakarta.validation.constraints."));
        if (constrained) {
            annotations.add(JavaAnnotationModel.marker(
                    "org.springframework.validation.annotation.Validated"));
        }
        return List.copyOf(annotations);
    }

    private static JavaMethodModel operation(
            JavaLayout layout,
            ApplicationEntity entity,
            ApplicationOperation operation,
            JavaTypeRef responseType,
            TreeSet<String> explicitImports) {
        ApplicationOperation.Endpoint endpoint = operation.endpoint().orElseThrow();
        List<JavaParameterModel> parameters = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        for (ApplicationOperation.RequestMapping mapping : endpoint.request()) {
            parameters.add(parameter(layout, entity, operation, mapping));
        }
        if (operation.requiresId()) {
            arguments.add(ID_PARAMETER);
        }
        operation.requestTypeName().ifPresent(ignored -> arguments.add(REQUEST_PARAMETER));

        List<String> statements = new ArrayList<>();
        if (operation.requestTypeName().isPresent() && !endpoint.hasBody()) {
            String request = operation.requestTypeName().orElseThrow();
            // The request type is named only inside a statement, where the import resolver cannot
            // see it. A body-bound request is a parameter and is resolved from its type instead.
            explicitImports.add(layout.packageName(JavaLayout.DTO) + "." + request);
            String values = operation.input().stream()
                    .map(ApplicationField::name)
                    .collect(java.util.stream.Collectors.joining(", "));
            statements.add(request + " " + REQUEST_PARAMETER + " = new "
                    + request + "(" + values + ");");
        }
        String call = SERVICE_FIELD + "." + operation.methodName()
                + "(" + String.join(", ", arguments) + ")";
        int status = operation.result().status();
        if (operation.result().kind() == ApplicationOperation.ResultKind.NOTHING) {
            statements.add(call + ";");
            statements.add("return ResponseEntity.status(" + status + ").build();");
        } else {
            statements.add("return ResponseEntity.status(" + status + ").body(" + call + ");");
        }

        return new JavaMethodModel(
                operation.methodName(),
                JavaTypeRef.parameterized(
                        "org.springframework.http.ResponseEntity",
                        bodyType(layout, operation, responseType)),
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(mapping(endpoint)),
                parameters,
                statements,
                Optional.of(endpoint.endpointWhere()));
    }

    private static JavaParameterModel parameter(
            JavaLayout layout,
            ApplicationEntity entity,
            ApplicationOperation operation,
            ApplicationOperation.RequestMapping mapping) {
        if (mapping instanceof ApplicationOperation.Body) {
            List<JavaAnnotationModel> annotations = new ArrayList<>();
            if (validates(operation)) {
                annotations.add(JavaAnnotationModel.marker("jakarta.validation.Valid"));
            }
            annotations.add(JavaAnnotationModel.marker(ANNOTATIONS + "RequestBody"));
            return new JavaParameterModel(
                    REQUEST_PARAMETER,
                    JavaTypeRef.of(layout.packageName(JavaLayout.DTO) + "."
                            + operation.requestTypeName().orElseThrow()),
                    annotations);
        }

        ApplicationField field = mapping.input().equals(ID_PARAMETER)
                ? entity.idField()
                : operation.input().stream()
                        .filter(candidate -> candidate.name().equals(mapping.input()))
                        .findFirst()
                        .orElseThrow();
        String annotation;
        String externalName;
        if (mapping instanceof ApplicationOperation.Path value) {
            annotation = "PathVariable";
            externalName = value.parameter();
        } else if (mapping instanceof ApplicationOperation.Query value) {
            annotation = "RequestParam";
            externalName = value.parameter();
        } else if (mapping instanceof ApplicationOperation.Header value) {
            annotation = "RequestHeader";
            externalName = value.header();
        } else {
            throw new IllegalStateException("unknown request mapping " + mapping.getClass());
        }
        // A body is validated by @Valid at this same boundary. A value bound from a parameter
        // deserves the same treatment: validation the specification declared belongs where the
        // request enters, not only deeper in the service.
        List<JavaAnnotationModel> annotations = new ArrayList<>(
                validates(operation) ? VALIDATION.map(field) : List.of());
        annotations.add(JavaAnnotationModel.of(
                ANNOTATIONS + annotation,
                new JavaAnnotationModel.Attribute("value", "\"" + externalName + "\"")));
        return new JavaParameterModel(
                mapping.input(), JavaTypeMapper.map(field.scalarType()), List.copyOf(annotations));
    }

    private static JavaTypeRef bodyType(
            JavaLayout layout, ApplicationOperation operation, JavaTypeRef responseType) {
        return switch (operation.result().kind()) {
            case ENTITY -> responseType;
            case LIST -> JavaTypeRef.parameterized("java.util.List", responseType);
            case PAGE -> JavaTypeRef.parameterized(
                    layout.packageName(JavaLayout.DTO) + ".PageResponse", responseType);
            case NOTHING -> JavaTypeRef.of("java.lang.Void");
        };
    }

    private static JavaAnnotationModel mapping(ApplicationOperation.Endpoint endpoint) {
        String annotation = switch (endpoint.method()) {
            case GET -> "GetMapping";
            case POST -> "PostMapping";
            case PUT -> "PutMapping";
            case DELETE -> "DeleteMapping";
        };
        return JavaAnnotationModel.of(
                ANNOTATIONS + annotation,
                new JavaAnnotationModel.Attribute(
                        "value", "\"" + endpoint.effectivePath() + "\""));
    }

    private static boolean validates(ApplicationOperation operation) {
        return operation.allInstructions().stream()
                .anyMatch(instruction -> instruction.command()
                        == ApplicationOperation.FlowCommand.VALIDATE_INPUT);
    }
}
