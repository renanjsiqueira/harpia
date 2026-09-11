package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationIntegration;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Materialises HTTP bindings of outbound ports as typed Spring {@code RestClient}s. */
public final class JavaSpringIntegrationClientTransformer {

    private static final String CLIENT_FIELD = "client";
    private static final String CONNECT_TIMEOUT = "2s";
    private static final String READ_TIMEOUT = "10s";
    private static final String TIMEOUTS = "IntegrationTimeouts";

    public List<JavaSourceFile> transform(JavaSpringContext context) {
        List<JavaSourceFile> files = context.application().integrations().stream()
                .filter(integration -> integration.operations().stream()
                        .anyMatch(operation -> operation.http().isPresent()))
                .flatMap(integration -> java.util.stream.Stream.of(
                        failure(context, integration), transform(context, integration)))
                .toList();
        return files.isEmpty()
                ? List.of()
                : java.util.stream.Stream.concat(files.stream(), java.util.stream.Stream.of(
                        timeouts(context, files))).toList();
    }

    /**
     * The deadline every outbound call carries.
     *
     * <p>A call that can hang forever is not a dependency, it is a thread held until something
     * else gives up. How long to wait follows the network and the agreement with the other side
     * rather than anything the specification said, so it is configuration.
     *
     * <p>It is a customizer and not a line in each client because Spring applies customizers to
     * the builder it hands out, and leaves a test free to install its own request factory on top —
     * which is how anyone would test a client that speaks HTTP.
     */
    private static JavaSourceFile timeouts(JavaSpringContext context, List<JavaSourceFile> files) {
        dev.harpia.diag.SourceRef where = files.getFirst().source().orElseThrow();
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                context.layout().packageName(JavaLayout.INTEGRATION),
                TIMEOUTS,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Applies a connect and read deadline to every generated client."),
                List.of(JavaAnnotationModel.marker(
                        "org.springframework.context.annotation.Configuration")),
                List.of(
                        new JavaImportModel("java.time.Duration"),
                        new JavaImportModel(
                                "org.springframework.beans.factory.annotation.Value"),
                        new JavaImportModel("org.springframework.context.annotation.Bean"),
                        new JavaImportModel(
                                "org.springframework.boot.web.client.RestClientCustomizer"),
                        new JavaImportModel(
                                "org.springframework.http.client.SimpleClientHttpRequestFactory")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new JavaMethodModel(
                        "harpiaIntegrationTimeouts",
                        JavaTypeRef.of(
                                "org.springframework.boot.web.client.RestClientCustomizer"),
                        JavaVisibility.PUBLIC,
                        Set.of(),
                        List.of(JavaAnnotationModel.marker(
                                "org.springframework.context.annotation.Bean")),
                        List.of(
                                new JavaParameterModel(
                                        "connectTimeout",
                                        JavaTypeRef.of("java.time.Duration"),
                                        List.of(JavaAnnotationModel.of(
                                                "org.springframework.beans.factory.annotation.Value",
                                                new JavaAnnotationModel.Attribute(
                                                        "value",
                                                        "\"${harpia.integration.connect-timeout:"
                                                                + CONNECT_TIMEOUT + "}\"")))),
                                new JavaParameterModel(
                                        "readTimeout",
                                        JavaTypeRef.of("java.time.Duration"),
                                        List.of(JavaAnnotationModel.of(
                                                "org.springframework.beans.factory.annotation.Value",
                                                new JavaAnnotationModel.Attribute(
                                                        "value",
                                                        "\"${harpia.integration.read-timeout:"
                                                                + READ_TIMEOUT + "}\""))))),
                        List.of(
                                "return builder -> {",
                                "    SimpleClientHttpRequestFactory requestFactory ="
                                        + " new SimpleClientHttpRequestFactory();",
                                "    requestFactory.setConnectTimeout(connectTimeout);",
                                "    requestFactory.setReadTimeout(readTimeout);",
                                "    builder.requestFactory(requestFactory);",
                                "};"),
                        Optional.of(where))),
                Optional.of(where));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.INTEGRATION), TIMEOUTS),
                type,
                Optional.of(where));
    }

    /**
     * The failure the port raises, which belongs to the port.
     *
     * <p>Without it a caller catches {@code RestClientResponseException} — Spring vocabulary
     * arriving through a declaration that never mentioned HTTP. The whole point of an Integration
     * is that the caller does not have to know how the other side is reached, and an exception is
     * part of what a caller has to know.
     *
     * <p>The status is present only when there was a response. A call that timed out or never
     * connected failed just as truly, and inventing a number for it would say something the
     * network never said.
     */
    private static JavaSourceFile failure(
            JavaSpringContext context, ApplicationIntegration integration) {
        String typeName = failureTypeName(integration.name());
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                context.layout().packageName(JavaLayout.INTEGRATION),
                typeName,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Raised when a call to the " + integration.name()
                        + " port does not succeed."),
                List.of(),
                List.of(),
                List.of(JavaTypeRef.of("java.lang.RuntimeException")),
                List.of(
                        new JavaFieldModel(
                                "serialVersionUID",
                                JavaTypeRef.of("long"),
                                JavaVisibility.PRIVATE,
                                Set.of(JavaModifier.STATIC, JavaModifier.FINAL),
                                List.of(),
                                Optional.of("1L"),
                                Optional.of(integration.where())),
                        new JavaFieldModel(
                                "operation",
                                JavaTypeRef.of("java.lang.String"),
                                JavaVisibility.PRIVATE,
                                Set.of(JavaModifier.FINAL),
                                List.of(),
                                Optional.empty(),
                                Optional.of(integration.where())),
                        new JavaFieldModel(
                                "status",
                                JavaTypeRef.of("java.lang.Integer"),
                                JavaVisibility.PRIVATE,
                                Set.of(JavaModifier.FINAL),
                                List.of(),
                                Optional.empty(),
                                Optional.of(integration.where()))),
                List.of(
                        new JavaConstructorModel(
                                JavaVisibility.PUBLIC,
                                List.of(),
                                List.of(
                                        new JavaParameterModel(
                                                "operation", JavaTypeRef.of("java.lang.String")),
                                        new JavaParameterModel(
                                                "status", JavaTypeRef.of("int"))),
                                List.of(
                                        "super(\"" + javaString(integration.name())
                                                + ".\" + operation + \" answered \" + status);",
                                        "this.operation = operation;",
                                        "this.status = status;"),
                                Optional.of(integration.where())),
                        new JavaConstructorModel(
                                JavaVisibility.PUBLIC,
                                List.of(),
                                List.of(
                                        new JavaParameterModel(
                                                "operation", JavaTypeRef.of("java.lang.String")),
                                        new JavaParameterModel(
                                                "reason", JavaTypeRef.of("java.lang.String"))),
                                List.of(
                                        "super(\"" + javaString(integration.name())
                                                + ".\" + operation + \": \" + reason);",
                                        "this.operation = operation;",
                                        "this.status = null;"),
                                Optional.of(integration.where())),
                        new JavaConstructorModel(
                                JavaVisibility.PUBLIC,
                                List.of(),
                                List.of(
                                        new JavaParameterModel(
                                                "operation", JavaTypeRef.of("java.lang.String")),
                                        new JavaParameterModel(
                                                "cause", JavaTypeRef.of("java.lang.Throwable"))),
                                List.of(
                                        "super(\"" + javaString(integration.name())
                                                + ".\" + operation + \" could not be reached\","
                                                + " cause);",
                                        "this.operation = operation;",
                                        "this.status = null;"),
                                Optional.of(integration.where()))),
                List.of(
                        new JavaMethodModel(
                                "operation",
                                JavaTypeRef.of("java.lang.String"),
                                JavaVisibility.PUBLIC,
                                Set.of(),
                                List.of(),
                                List.of(),
                                List.of("return operation;"),
                                Optional.of(integration.where())),
                        new JavaMethodModel(
                                "status",
                                JavaTypeRef.parameterized(
                                        "java.util.Optional",
                                        JavaTypeRef.of("java.lang.Integer")),
                                JavaVisibility.PUBLIC,
                                Set.of(),
                                List.of(),
                                List.of(),
                                List.of("return Optional.ofNullable(status);"),
                                Optional.of(integration.where()))),
                Optional.of(integration.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.INTEGRATION), typeName),
                type,
                Optional.of(integration.where()));
    }

    public static String failureTypeName(String integration) {
        return integration + "Exception";
    }

    private JavaSourceFile transform(
            JavaSpringContext context, ApplicationIntegration integration) {
        String packageName = context.layout().packageName(JavaLayout.INTEGRATION);
        String domainPackage = context.layout().packageName(JavaLayout.DOMAIN);
        String typeName = clientTypeName(integration.name());
        TreeSet<String> imports = new TreeSet<>();
        imports.add("java.net.URI");
        imports.add("org.springframework.web.util.UriComponentsBuilder");

        List<JavaMethodModel> methods = new ArrayList<>(integration.operations().stream()
                .filter(operation -> operation.http().isPresent())
                .map(operation -> method(
                        operation, domainPackage, failureTypeName(integration.name()), imports))
                .toList());
        if (returnsAnything(integration)) {
            imports.add("java.util.stream.Collectors");
            methods.add(checker(integration, failureTypeName(integration.name())));
        }
        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                packageName,
                typeName,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("HTTP provider generated for the " + integration.name()
                        + " outbound port."),
                List.of(JavaAnnotationModel.marker("org.springframework.stereotype.Component")),
                imports.stream().map(JavaImportModel::new).toList(),
                List.of(),
                fields(integration),
                List.of(constructor(integration)),
                methods,
                Optional.of(integration.where()));
        return new JavaSourceFile(
                JavaLayout.sourcePath(
                        context.layout().packagePath(JavaLayout.INTEGRATION), typeName),
                type,
                Optional.of(integration.where()));
    }

    /**
     * The client takes the builder as Spring hands it over, and nothing else.
     *
     * <p>The deadline is applied by a customizer rather than here. Setting a request factory in
     * this constructor would silently replace the one {@code MockRestServiceServer} installs, and
     * a generated client nobody can test the ordinary way is a worse deal than one whose timeout
     * lives one file over.
     */
    private static JavaConstructorModel constructor(ApplicationIntegration integration) {
        if (!returnsAnything(integration)) {
            return new JavaConstructorModel(
                    JavaVisibility.PUBLIC,
                    List.of(),
                    List.of(new JavaParameterModel(
                            "builder",
                            JavaTypeRef.of("org.springframework.web.client.RestClient.Builder"))),
                    List.of("this.client = builder.build();"),
                    Optional.of(integration.where()));
        }
        return new JavaConstructorModel(
                JavaVisibility.PUBLIC,
                List.of(),
                List.of(
                        new JavaParameterModel(
                                "builder",
                                JavaTypeRef.of(
                                        "org.springframework.web.client.RestClient.Builder")),
                        new JavaParameterModel(
                                "validator", JavaTypeRef.of("jakarta.validation.Validator"))),
                List.of(
                        "this.client = builder.build();",
                        "this.validator = validator;"),
                Optional.of(integration.where()));
    }

    private static List<JavaFieldModel> fields(ApplicationIntegration integration) {
        List<JavaFieldModel> fields = new ArrayList<>();
        fields.add(new JavaFieldModel(
                CLIENT_FIELD,
                JavaTypeRef.of("org.springframework.web.client.RestClient"),
                JavaVisibility.PRIVATE,
                Set.of(JavaModifier.FINAL),
                List.of(),
                Optional.empty(),
                Optional.of(integration.where())));
        if (returnsAnything(integration)) {
            fields.add(new JavaFieldModel(
                    "validator",
                    JavaTypeRef.of("jakarta.validation.Validator"),
                    JavaVisibility.PRIVATE,
                    Set.of(JavaModifier.FINAL),
                    List.of(),
                    Optional.empty(),
                    Optional.of(integration.where())));
        }
        return List.copyOf(fields);
    }

    private static boolean returnsAnything(ApplicationIntegration integration) {
        return integration.operations().stream()
                .filter(operation -> operation.http().isPresent())
                .anyMatch(operation -> operation.output().type().isPresent());
    }

    /**
     * Checks the response against the contract the port declared.
     *
     * <p>The declaration says what comes back, and a body that does not satisfy it is not a
     * smaller answer — it is not an answer. Without this a missing required field arrives as
     * {@code null} and the flow carries on as if the other side had agreed.
     *
     * <p>The violations are sorted so the same bad response always produces the same message.
     */
    private static JavaMethodModel checker(
            ApplicationIntegration integration, String failureType) {
        return new JavaMethodModel(
                "check",
                JavaTypeRef.of("void"),
                JavaVisibility.PRIVATE,
                Set.of(),
                List.of(),
                List.of(
                        new JavaParameterModel("operation", JavaTypeRef.of("java.lang.String")),
                        new JavaParameterModel("body", JavaTypeRef.of("java.lang.Object"))),
                List.of(
                        "if (body == null) {",
                        "    throw new " + failureType
                                + "(operation, \"the response carried no body\");",
                        "}",
                        "String violations = validator.validate(body).stream()",
                        "        .map(violation -> violation.getPropertyPath() + \" \" "
                                + "+ violation.getMessage())",
                        "        .sorted()",
                        "        .collect(Collectors.joining(\", \"));",
                        "if (!violations.isEmpty()) {",
                        "    throw new " + failureType + "(operation, violations);",
                        "}"),
                Optional.of(integration.where()));
    }

    /** {@code FraudService} is configured as {@code fraud-service}, the way Spring reads keys. */
    static String propertyName(String integration) {
        return integration.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static JavaMethodModel method(
            ApplicationIntegration.Operation operation,
            String domainPackage,
            String failureType,
            TreeSet<String> imports) {
        ApplicationIntegration.Http http = operation.http().orElseThrow();
        List<JavaParameterModel> parameters = operation.input().stream()
                .map(parameter -> new JavaParameterModel(
                        parameter.name(), JavaTypeMapper.map(parameter.type(), domainPackage)))
                .toList();
        JavaTypeRef result = operation.output().type()
                .map(type -> JavaTypeMapper.map(type, domainPackage))
                .orElseGet(() -> JavaTypeRef.of("void"));
        List<String> statements = new ArrayList<>();
        statements.add("URI uri = UriComponentsBuilder.fromUriString(\""
                + javaString(http.effectiveUrl()) + "\")");
        http.request().stream()
                .filter(ApplicationOperation.Query.class::isInstance)
                .map(ApplicationOperation.Query.class::cast)
                .forEach(mapping -> statements.add("        .queryParam(\""
                        + javaString(mapping.parameter()) + "\", " + mapping.input() + ")"));
        List<String> pathArguments = pathParameters(http.path()).stream()
                .map(parameter -> http.request().stream()
                        .filter(ApplicationOperation.Path.class::isInstance)
                        .map(ApplicationOperation.Path.class::cast)
                        .filter(mapping -> mapping.parameter().equals(parameter))
                        .map(ApplicationOperation.Path::input)
                        .findFirst()
                        .orElseThrow())
                .toList();
        statements.add("        .buildAndExpand(" + String.join(", ", pathArguments) + ")");
        statements.add("        .toUri();");

        boolean hasBody = http.request().stream().anyMatch(ApplicationOperation.Body.class::isInstance);
        if (hasBody) {
            imports.add("java.util.LinkedHashMap");
            imports.add("java.util.Map");
            statements.add("Map<String, Object> body = new LinkedHashMap<>();");
            Set<String> pathInputs = http.request().stream()
                    .filter(ApplicationOperation.Path.class::isInstance)
                    .map(ApplicationOperation.RequestMapping::input)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            operation.input().stream()
                    .filter(parameter -> !pathInputs.contains(parameter.name()))
                    .forEach(parameter -> statements.add("body.put(\""
                            + javaString(parameter.name()) + "\", " + parameter.name() + ");"));
        }

        // Both failure paths are wrapped: a response that says no, and a call that never got
        // one. Either way the caller catches the port's exception and not Spring's.
        statements.add("try {");
        // A body that does not satisfy the declared contract is not a smaller answer; it is not
        // an answer. So it is named, checked, and only then returned.
        String prefix = operation.output().type().isPresent()
                ? "    " + result.sourceName() + " answer = "
                : "    ";
        statements.add(prefix + "client."
                + http.method().name().toLowerCase(Locale.ROOT) + "()");
        statements.add("            .uri(uri)");
        http.request().stream()
                .filter(ApplicationOperation.Header.class::isInstance)
                .map(ApplicationOperation.Header.class::cast)
                .forEach(mapping -> statements.add("            .header(\""
                        + javaString(mapping.header()) + "\", String.valueOf("
                        + mapping.input() + "))"));
        if (hasBody) {
            statements.add("            .body(body)");
        }
        imports.add("org.springframework.http.HttpStatusCode");
        imports.add("org.springframework.web.client.ResourceAccessException");
        statements.add("            .retrieve()");
        statements.add("            .onStatus(HttpStatusCode::isError, (request, response) -> {");
        statements.add("                throw new " + failureType + "(\""
                + javaString(lowerFirst(operation.name()))
                + "\", response.getStatusCode().value());");
        statements.add("            })");
        if (operation.output().type().isEmpty()) {
            statements.add("            .toBodilessEntity();");
        } else if (result.arguments().isEmpty()) {
            statements.add("            .body(" + result.simpleName() + ".class);");
        } else {
            imports.add("org.springframework.core.ParameterizedTypeReference");
            statements.add("            .body(new ParameterizedTypeReference<"
                    + result.sourceName() + ">() {});");
        }
        if (operation.output().type().isPresent()) {
            statements.add("    check(\"" + javaString(lowerFirst(operation.name()))
                    + "\", answer);");
            statements.add("    return answer;");
        }
        statements.add("} catch (ResourceAccessException exception) {");
        statements.add("    throw new " + failureType + "(\""
                + javaString(lowerFirst(operation.name())) + "\", exception);");
        statements.add("}");
        return new JavaMethodModel(
                lowerFirst(operation.name()),
                result,
                JavaVisibility.PUBLIC,
                Set.of(),
                List.of(),
                parameters,
                statements,
                Optional.of(http.endpointWhere()));
    }

    public static String clientTypeName(String integration) {
        return integration + "Client";
    }

    public static String clientFieldName(String integration) {
        return lowerFirst(integration) + "Client";
    }

    public static String operationMethodName(String operation) {
        return lowerFirst(operation);
    }

    private static String lowerFirst(String value) {
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private static String javaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> pathParameters(String path) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{([^}/]+)}")
                .matcher(path);
        List<String> parameters = new ArrayList<>();
        while (matcher.find()) {
            parameters.add(matcher.group(1));
        }
        return List.copyOf(parameters);
    }
}
