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

    public List<JavaSourceFile> transform(JavaSpringContext context) {
        return context.application().integrations().stream()
                .filter(integration -> integration.operations().stream()
                        .anyMatch(operation -> operation.http().isPresent()))
                .map(integration -> transform(context, integration))
                .toList();
    }

    private JavaSourceFile transform(
            JavaSpringContext context, ApplicationIntegration integration) {
        String packageName = context.layout().packageName(JavaLayout.INTEGRATION);
        String domainPackage = context.layout().packageName(JavaLayout.DOMAIN);
        String typeName = clientTypeName(integration.name());
        TreeSet<String> imports = new TreeSet<>();
        imports.add("java.net.URI");
        imports.add("org.springframework.http.client.SimpleClientHttpRequestFactory");
        imports.add("org.springframework.web.util.UriComponentsBuilder");

        List<JavaMethodModel> methods = integration.operations().stream()
                .filter(operation -> operation.http().isPresent())
                .map(operation -> method(operation, domainPackage, imports))
                .toList();
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
                List.of(new JavaFieldModel(
                        CLIENT_FIELD,
                        JavaTypeRef.of("org.springframework.web.client.RestClient"),
                        JavaVisibility.PRIVATE,
                        Set.of(JavaModifier.FINAL),
                        List.of(),
                        Optional.empty(),
                        Optional.of(integration.where()))),
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
     * Builds the client with a deadline on every call.
     *
     * <p>A call that can hang forever is not a dependency, it is a thread held until something
     * else gives up. How long to wait is a deployment fact — it follows the network and the
     * agreement with the other side, not anything the specification said — so it is read from
     * configuration, with a default that is short enough to fail rather than accumulate.
     */
    private static JavaConstructorModel constructor(ApplicationIntegration integration) {
        String prefix = "harpia.integration." + propertyName(integration.name());
        return new JavaConstructorModel(
                JavaVisibility.PUBLIC,
                List.of(),
                List.of(
                        new JavaParameterModel(
                                "builder",
                                JavaTypeRef.of(
                                        "org.springframework.web.client.RestClient.Builder")),
                        new JavaParameterModel(
                                "connectTimeout",
                                JavaTypeRef.of("java.time.Duration"),
                                List.of(JavaAnnotationModel.of(
                                        "org.springframework.beans.factory.annotation.Value",
                                        new JavaAnnotationModel.Attribute(
                                                "value",
                                                "\"${" + prefix + ".connect-timeout:"
                                                        + CONNECT_TIMEOUT + "}\"")))),
                        new JavaParameterModel(
                                "readTimeout",
                                JavaTypeRef.of("java.time.Duration"),
                                List.of(JavaAnnotationModel.of(
                                        "org.springframework.beans.factory.annotation.Value",
                                        new JavaAnnotationModel.Attribute(
                                                "value",
                                                "\"${" + prefix + ".read-timeout:"
                                                        + READ_TIMEOUT + "}\""))))),
                List.of(
                        "SimpleClientHttpRequestFactory requestFactory = "
                                + "new SimpleClientHttpRequestFactory();",
                        "requestFactory.setConnectTimeout(connectTimeout);",
                        "requestFactory.setReadTimeout(readTimeout);",
                        "this.client = builder.requestFactory(requestFactory).build();"),
                Optional.of(integration.where()));
    }

    /** {@code FraudService} is configured as {@code fraud-service}, the way Spring reads keys. */
    static String propertyName(String integration) {
        return integration.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static JavaMethodModel method(
            ApplicationIntegration.Operation operation,
            String domainPackage,
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

        String prefix = operation.output().type().isPresent() ? "return " : "";
        statements.add(prefix + "client."
                + http.method().name().toLowerCase(Locale.ROOT) + "()");
        statements.add("        .uri(uri)");
        http.request().stream()
                .filter(ApplicationOperation.Header.class::isInstance)
                .map(ApplicationOperation.Header.class::cast)
                .forEach(mapping -> statements.add("        .header(\""
                        + javaString(mapping.header()) + "\", String.valueOf("
                        + mapping.input() + "))"));
        if (hasBody) {
            statements.add("        .body(body)");
        }
        statements.add("        .retrieve()");
        if (operation.output().type().isEmpty()) {
            statements.add("        .toBodilessEntity();");
        } else if (result.arguments().isEmpty()) {
            statements.add("        .body(" + result.simpleName() + ".class);");
        } else {
            imports.add("org.springframework.core.ParameterizedTypeReference");
            statements.add("        .body(new ParameterizedTypeReference<"
                    + result.sourceName() + ">() {});");
        }
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
