package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationField;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.application.ApplicationOperation.FailureCondition;
import dev.harpia.application.ApplicationOperation.FlowCommand;
import dev.harpia.application.ApplicationOperation.ResultKind;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaSampleValues;
import dev.harpia.target.javaspring.mapping.SqlConstraintNames;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaModifier;
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
 * Generates a web test per endpoint, and one per failure the specification declares.
 *
 * <p>The service is mocked, so what is under test is the HTTP contract: the declared status, the
 * path binding, and whether a declared failure really produces the status it declares. The
 * duplicate test names the same constraint the migration creates, which is what proves the schema
 * and the error handler still agree.
 */
public final class JavaSpringControllerTestTransformer {

    // Statements use simple names; the qualified names below are what the file imports.
    private static final String MOCKITO = "Mockito";
    private static final String MATCHERS = "ArgumentMatchers";
    private static final String REQUESTS = "MockMvcRequestBuilders";
    private static final String MATCHERS_RESULT = "MockMvcResultMatchers";
    private static final List<String> FIXED_IMPORTS = List.of(
            "org.junit.jupiter.api.Test",
            "org.mockito.ArgumentMatchers",
            "org.mockito.Mockito",
            "org.springframework.http.MediaType",
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders",
            "org.springframework.test.web.servlet.result.MockMvcResultMatchers");

    public JavaSourceFile transform(JavaSpringContext context, ApplicationEntity entity) {
        Names names = Names.of(context, entity);
        List<ApplicationOperation> operations = entity.operations().stream()
                .filter(operation -> operation.endpoint().isPresent())
                .toList();
        TreeSet<String> imports = new TreeSet<>(FIXED_IMPORTS);
        imports.add(names.responseImport());
        // A paged operation answers with the envelope, so the test has to name it too.
        if (entity.operations().stream().anyMatch(operation ->
                operation.result().kind() == ApplicationOperation.ResultKind.PAGE)) {
            imports.add(names.dtoPackage() + ".PageResponse");
        }
        entity.fields().forEach(field ->
                imports.addAll(JavaSampleValues.requiredImports(
                        field, context.layout().packageName(JavaLayout.DOMAIN))));
        for (ApplicationOperation operation : operations) {
            boolean notFound = operation.failures().stream()
                    .anyMatch(failure -> failure.condition() == FailureCondition.NOT_FOUND);
            if (notFound && has(operation, FlowCommand.LOAD_BY_ID)) {
                imports.add(names.notFoundImport());
                JavaSampleValues.requiredImport(entity.idField().scalarType())
                        .ifPresent(imports::add);
            }
        }

        boolean secured = context.application().capabilities()
                .requires(dev.harpia.capability.Capability.SECURITY);
        if (secured) {
            imports.add(context.layout().packageName(JavaLayout.CONFIG) + ".SecurityConfig");
        }
        List<JavaMethodModel> methods = new ArrayList<>();
        for (ApplicationOperation operation : operations) {
            // An authenticated endpoint cannot be exercised without an identity, and asserting
            // its declared status from an anonymous request would only assert the refusal wearing
            // the wrong number. What it can prove is that the refusal happens.
            if (authenticated(operation)) {
                methods.add(refusesAnonymous(names, entity, operation, imports));
                continue;
            }
            methods.add(declaredStatus(names, entity, operation, imports));
            for (ApplicationOperation.Failure failure : operation.failures()) {
                failure(names, entity, operation, failure, imports).ifPresent(methods::add);
            }
        }
        methods.add(sampleResponse(names, entity));

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                names.webPackage(),
                JavaLayout.testTypeName(names.controllerName()),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                Optional.of("Every endpoint declared for " + entity.typeName()
                        + ", and every failure it declares."),
                // A slice test auto-configures security when it is on the classpath, but not the
                // chain this project declared. Without the import every endpoint would be judged
                // by Spring Boot's default rule, and even a public one would answer 401.
                secured
                        ? List.of(
                                JavaAnnotationModel.of(
                                        "org.springframework.boot.test.autoconfigure.web.servlet"
                                                + ".WebMvcTest",
                                        new JavaAnnotationModel.Attribute(
                                                "value", names.controllerName() + ".class")),
                                JavaAnnotationModel.of(
                                        "org.springframework.context.annotation.Import",
                                        new JavaAnnotationModel.Attribute(
                                                "value", "SecurityConfig.class")))
                        : List.of(JavaAnnotationModel.of(
                                "org.springframework.boot.test.autoconfigure.web.servlet"
                                        + ".WebMvcTest",
                                new JavaAnnotationModel.Attribute(
                                        "value", names.controllerName() + ".class"))),
                imports.stream().map(JavaImportModel::new).toList(),
                List.of(),
                List.of(identifier(entity), mockMvc(), mockedService(names)),
                List.of(),
                methods,
                Optional.of(entity.where()));
        return new JavaSourceFile(
                JavaLayout.testSourcePath(
                        names.webPath(), JavaLayout.testTypeName(names.controllerName())),
                type,
                Optional.of(entity.where()));
    }

    private static JavaFieldModel identifier(ApplicationEntity entity) {
        return new JavaFieldModel(
                "ID",
                dev.harpia.target.javaspring.mapping.JavaTypeMapper.map(entity.idField().scalarType()),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.STATIC, JavaModifier.FINAL),
                List.of(),
                Optional.of(JavaSampleValues.java(entity.idField())),
                Optional.of(entity.where()));
    }

    private static JavaFieldModel mockMvc() {
        return new JavaFieldModel(
                "mockMvc",
                JavaTypeRef.of("org.springframework.test.web.servlet.MockMvc"),
                JavaVisibility.PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker(
                        "org.springframework.beans.factory.annotation.Autowired")),
                Optional.empty(),
                Optional.empty());
    }

    private static JavaFieldModel mockedService(Names names) {
        return new JavaFieldModel(
                "service",
                names.serviceType(),
                JavaVisibility.PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker(
                        "org.springframework.boot.test.mock.mockito.MockBean")),
                Optional.empty(),
                Optional.empty());
    }

    private JavaMethodModel declaredStatus(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            TreeSet<String> imports) {
        List<String> statements = new ArrayList<>();
        stubResult(names, operation, statements, imports);
        statements.add("");
        perform(names, entity, operation, statements, body(operation), List.of(
                MATCHERS_RESULT + ".status().is(" + operation.result().status() + ")"));
        return test(operation, "ReturnsItsDeclaredStatus", statements);
    }

    /**
     * The one thing an anonymous request can prove about an authenticated endpoint.
     *
     * <p>401 and not 403: the request carried no identity at all, and saying "forbidden" would
     * describe someone who was recognised and then turned away.
     */
    private JavaMethodModel refusesAnonymous(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            TreeSet<String> imports) {
        List<String> statements = new ArrayList<>();
        // The body matters as much as the status: a refusal that answered in a shape of Spring's
        // choosing would leave the API with two error contracts and callers handling both.
        perform(names, entity, operation, statements, body(operation), List.of(
                MATCHERS_RESULT + ".status().is(401)",
                MATCHERS_RESULT + ".jsonPath(\"$.status\").value(401)",
                MATCHERS_RESULT + ".jsonPath(\"$.error\").value(\"unauthorized\")"));
        return test(operation, "RefusesAnAnonymousRequest", statements);
    }

    private static boolean authenticated(ApplicationOperation operation) {
        return operation.endpoint()
                .map(endpoint -> endpoint.access().requiresIdentity())
                .orElse(false);
    }

    private Optional<JavaMethodModel> failure(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            ApplicationOperation.Failure failure,
            TreeSet<String> imports) {
        List<String> statements = new ArrayList<>();
        int status = failure.status();
        switch (failure.condition()) {
            case INVALID_INPUT -> {
                if (operation.input().isEmpty()) {
                    return Optional.empty();
                }
                if (operation.endpoint().orElseThrow().hasBody()) {
                    perform(names, entity, operation, statements, Optional.of("{}"), List.of(
                            MATCHERS_RESULT + ".status().is(" + status + ")"));
                    return Optional.of(test(operation, "RejectsAnIncompleteBody", statements));
                }
                // Bound from parameters, an empty body proves nothing: the request has no body.
                // Only a value that is present and invalid exercises the declared validation.
                Optional<String> blank = blankable(operation);
                if (blank.isEmpty()) {
                    return Optional.empty();
                }
                perform(names, entity, operation, statements, Optional.empty(), List.of(
                        MATCHERS_RESULT + ".status().is(" + status + ")"), blank);
                return Optional.of(test(operation, "RejectsAnInvalidParameter", statements));
            }
            case NOT_FOUND -> {
                if (!has(operation, FlowCommand.LOAD_BY_ID)) {
                    return Optional.empty();
                }
                statements.add(throwing(names, operation,
                        "new NotFoundException(\"" + entity.typeName() + "\", ID)"));
                statements.add("");
                perform(names, entity, operation, statements, body(operation), List.of(
                        MATCHERS_RESULT + ".status().is(" + status + ")"));
                return Optional.of(test(operation, "ReportsAMissingRecord", statements));
            }
            case DUPLICATE -> {
                Optional<String> column = failure.field().flatMap(name -> column(entity, name));
                if (column.isEmpty()) {
                    return Optional.empty();
                }
                imports.add("org.springframework.dao.DataIntegrityViolationException");
                String constraint = SqlConstraintNames.unique(entity.tableName(), column.get());
                statements.add("DataIntegrityViolationException conflict =");
                statements.add("        new DataIntegrityViolationException(");
                statements.add("                \"duplicate key value violates unique "
                        + "constraint \\\"" + constraint + "\\\"\");");
                statements.add(throwing(names, operation, "conflict"));
                statements.add("");
                perform(names, entity, operation, statements, body(operation), List.of(
                        MATCHERS_RESULT + ".status().is(" + status + ")",
                        MATCHERS_RESULT + ".jsonPath(\"$.message\")"
                                + ".value(\"" + failure.field().orElseThrow()
                                + " already exists\")"));
                return Optional.of(test(operation, "ReportsADuplicate", statements));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    /** Stubs the mocked service so the controller has something to return. */
    private static void stubResult(
            Names names,
            ApplicationOperation operation,
            List<String> statements,
            TreeSet<String> imports) {
        if (operation.result().kind() == ResultKind.NOTHING) {
            return;
        }
        String value = "sampleResponse()";
        if (operation.result().kind() == ResultKind.LIST) {
            imports.add("java.util.List");
            value = "List.of(sampleResponse())";
        }
        if (operation.result().kind() == ResultKind.PAGE) {
            imports.add("java.util.List");
            value = "new PageResponse<>(List.of(sampleResponse()), 0, 1, 1L, 1)";
        }
        statements.add(MOCKITO + ".when(service." + operation.methodName()
                + "(" + stubArguments(operation) + "))");
        statements.add("        .thenReturn(" + value + ");");
    }

    private static String throwing(
            Names names, ApplicationOperation operation, String exception) {
        String stub = operation.result().kind() == ResultKind.NOTHING
                ? MOCKITO + ".doThrow(" + exception + ").when(service)."
                        + operation.methodName() + "(" + stubArguments(operation) + ");"
                : MOCKITO + ".when(service." + operation.methodName()
                        + "(" + stubArguments(operation) + ")).thenThrow(" + exception + ");";
        return stub;
    }

    private static String stubArguments(ApplicationOperation operation) {
        List<String> arguments = new ArrayList<>();
        if (operation.requiresId()) {
            arguments.add(MATCHERS + ".any()");
        }
        if (operation.requestTypeName().isPresent()) {
            arguments.add(MATCHERS + ".any()");
        }
        return String.join(", ", arguments);
    }

    private static void perform(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            List<String> statements,
            Optional<String> body,
            List<String> expectations) {
        perform(names, entity, operation, statements, body, expectations, Optional.empty());
    }

    /**
     * @param blankInput the mapped input to send empty, so a request bound from parameters can be
     *     invalid in the way the specification declared rather than merely incomplete
     */
    private static void perform(
            Names names,
            ApplicationEntity entity,
            ApplicationOperation operation,
            List<String> statements,
            Optional<String> body,
            List<String> expectations,
            Optional<String> blankInput) {
        ApplicationOperation.Endpoint endpoint = operation.endpoint().orElseThrow();
        String method = endpoint.method().name().toLowerCase(java.util.Locale.ROOT);
        String path = "\"" + endpoint.effectivePath() + "\"";
        // A path expands one value per parameter, in the order the parameters appear.
        StringBuilder uriVariables = new StringBuilder();
        for (ApplicationOperation.RequestMapping mapping : endpoint.request()) {
            if (mapping instanceof ApplicationOperation.Path value) {
                uriVariables.append(", ").append(value.input().equals("id")
                        ? "ID"
                        : requestValue(operation, value.input(), blankInput));
            }
        }
        String open = "mockMvc.perform(" + REQUESTS + "." + method
                + "(" + path + uriVariables + ")";
        for (ApplicationOperation.RequestMapping mapping : endpoint.request()) {
            if (mapping instanceof ApplicationOperation.Query value) {
                open += ".queryParam(\"" + value.parameter() + "\", "
                        + requestValue(operation, value.input(), blankInput) + ")";
            } else if (mapping instanceof ApplicationOperation.Header value) {
                open += ".header(\"" + value.header() + "\", "
                        + requestValue(operation, value.input(), blankInput) + ")";
            }
        }
        if (body.isEmpty() || !endpoint.hasBody()) {
            statements.add(open + ")");
        } else {
            statements.add(open);
            statements.add("                .contentType(MediaType.APPLICATION_JSON)");
            statements.add("                .content(\"" + body.orElseThrow() + "\"))");
        }
        for (int index = 0; index < expectations.size(); index++) {
            statements.add("        .andExpect(" + expectations.get(index) + ")"
                    + (index == expectations.size() - 1 ? ";" : ""));
        }
    }

    /** The first mapped input that is required text, which is blank-invalid. */
    private static Optional<String> blankable(ApplicationOperation operation) {
        for (ApplicationOperation.RequestMapping mapping
                : operation.endpoint().orElseThrow().request()) {
            if (!(mapping instanceof ApplicationOperation.Query)
                    && !(mapping instanceof ApplicationOperation.Header)) {
                continue;
            }
            Optional<ApplicationField> field = operation.input().stream()
                    .filter(candidate -> candidate.name().equals(mapping.input()))
                    .findFirst();
            if (field.filter(ApplicationField::required).filter(JavaSpringControllerTestTransformer
                    ::isText).isPresent()) {
                return Optional.of(mapping.input());
            }
        }
        return Optional.empty();
    }

    /** Whether the field holds text, which a declared enum does not: it holds one of its names. */
    private static boolean isText(ApplicationField field) {
        return field.present().scalarKind()
                .filter(kind -> kind == dev.harpia.application.ApplicationScalarType.STRING
                        || kind == dev.harpia.application.ApplicationScalarType.TEXT
                        || kind == dev.harpia.application.ApplicationScalarType.EMAIL)
                .isPresent();
    }

    private static Optional<String> body(ApplicationOperation operation) {
        if (operation.input().isEmpty()
                || operation.endpoint().stream().noneMatch(ApplicationOperation.Endpoint::hasBody)) {
            return Optional.empty();
        }
        List<String> entries = operation.input().stream()
                .map(field -> "\\\"" + field.name() + "\\\":" + JavaSampleValues.json(field))
                .toList();
        return Optional.of("{" + String.join(",", entries) + "}");
    }

    private static String requestValue(
            ApplicationOperation operation, String input, Optional<String> blankInput) {
        if (blankInput.filter(input::equals).isPresent()) {
            return "\"\"";
        }
        ApplicationField field = operation.input().stream()
                .filter(candidate -> candidate.name().equals(input))
                .findFirst()
                .orElseThrow();
        return "\"" + JavaSampleValues.plain(field) + "\"";
    }

    private static JavaMethodModel sampleResponse(Names names, ApplicationEntity entity) {
        String arguments = entity.fields().stream()
                .map(JavaSampleValues::java)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return new JavaMethodModel(
                "sampleResponse",
                names.responseType(),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.STATIC),
                List.of(),
                List.of(),
                List.of("return new " + entity.responseTypeName() + "(" + arguments + ");"),
                Optional.of(entity.where()));
    }

    private static JavaMethodModel test(
            ApplicationOperation operation, String suffix, List<String> statements) {
        return new JavaMethodModel(
                operation.methodName() + suffix,
                JavaTypeRef.of("void"),
                JavaVisibility.PACKAGE_PRIVATE,
                Set.of(),
                List.of(JavaAnnotationModel.marker("org.junit.jupiter.api.Test")),
                List.of(),
                statements,
                // MockMvc.perform is declared to throw, so every web test does too.
                List.of(JavaTypeRef.of("java.lang.Exception")),
                Optional.of(operation.where()));
    }

    private static Optional<String> column(ApplicationEntity entity, String fieldName) {
        return entity.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .map(ApplicationField::columnName)
                .findFirst();
    }

    private static boolean has(ApplicationOperation operation, FlowCommand command) {
        return operation.allInstructions().stream()
                .anyMatch(instruction -> instruction.command() == command);
    }

    /** Java names this transformer needs, resolved once. */
    private record Names(
            String webPackage,
            String webPath,
            String controllerName,
            String dtoPackage,
            String errorPackage,
            String responseName,
            JavaTypeRef serviceType,
            JavaTypeRef responseType) {

        private static Names of(JavaSpringContext context, ApplicationEntity entity) {
            JavaLayout layout = context.layout();
            return new Names(
                    layout.packageName(JavaLayout.WEB),
                    layout.packagePath(JavaLayout.WEB),
                    JavaLayout.controllerTypeName(entity.typeName()),
                    layout.packageName(JavaLayout.DTO),
                    layout.packageName(JavaLayout.ERROR),
                    entity.responseTypeName(),
                    JavaTypeRef.of(layout.packageName(JavaLayout.SERVICE) + "."
                            + JavaLayout.serviceTypeName(entity.typeName())),
                    JavaTypeRef.of(
                            layout.packageName(JavaLayout.DTO) + "." + entity.responseTypeName()));
        }

        private String responseImport() {
            return dtoPackage + "." + responseName;
        }

        private String notFoundImport() {
            return errorPackage + ".NotFoundException";
        }
    }
}
