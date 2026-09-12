package dev.harpia.target.javaspring.transformer;

import dev.harpia.application.ApplicationEntity;
import dev.harpia.application.ApplicationOperation;
import dev.harpia.capability.Capability;
import dev.harpia.diag.SourceRef;
import dev.harpia.target.javaspring.JavaLayout;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaImportModel;
import dev.harpia.target.javaspring.model.JavaMethodModel;
import dev.harpia.target.javaspring.model.JavaParameterModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import dev.harpia.target.javaspring.model.JavaTypeModel;
import dev.harpia.target.javaspring.model.JavaTypeRef;
import dev.harpia.target.javaspring.model.JavaVisibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns the access each endpoint declares into one filter chain.
 *
 * <p>Only generated when some operation is {@code authenticated}. A project where everything is
 * public has nothing to enforce, and adding a security dependency to it would change how it starts
 * for no reason anyone asked for.
 *
 * <p>The chain ends in {@code denyAll}. Every route Harpia generated is listed above it, so the
 * default only ever catches a path nobody declared — and refusing those is the safer answer than
 * letting an undeclared route inherit whatever the last rule happened to be.
 */
public final class JavaSpringSecurityTransformer {

    private static final String TYPE_NAME = "SecurityConfig";

    public List<JavaSourceFile> transform(JavaSpringContext context) {
        if (!context.application().capabilities().requires(Capability.SECURITY)) {
            return List.of();
        }
        List<Route> routes = routes(context);
        SourceRef where = context.application().entities().stream()
                .flatMap(entity -> entity.operations().stream())
                .flatMap(operation -> operation.endpoint().stream())
                .map(ApplicationOperation.Endpoint::endpointWhere)
                .findFirst()
                .orElseThrow();

        List<String> statements = new ArrayList<>();
        statements.add("return http");
        // A stateless API reads its identity from the request, so there is no session for a
        // forged form post to ride on, and no cookie for CSRF to protect.
        statements.add("        .csrf(csrf -> csrf.disable())");
        statements.add("        .sessionManagement(session ->");
        statements.add("                session.sessionCreationPolicy(SessionCreationPolicy"
                + ".STATELESS))");
        statements.add("        .authorizeHttpRequests(requests -> requests");
        routes.forEach(route -> statements.add(
                "                .requestMatchers(HttpMethod." + route.method() + ", \""
                        + route.path() + "\")." + rule(route.access())));
        statements.add("                .anyRequest().denyAll())");
        // A refusal here happens in the filter chain, before any @ExceptionHandler can see it, so
        // without this the generated API would answer refusals in a shape of Spring's choosing
        // while answering every declared failure in its own.
        statements.add("        .exceptionHandling(handling -> handling");
        statements.add("                .authenticationEntryPoint((request, response, failure) ->");
        statements.add("                        write(json, response, 401, \"unauthorized\",");
        statements.add("                                \"this endpoint requires an identity\"))");
        statements.add("                .accessDeniedHandler((request, response, failure) ->");
        statements.add("                        write(json, response, 403, \"forbidden\",");
        statements.add("                                \"this identity is not allowed here\")))");
        statements.add("        " + mechanism(context));
        statements.add("        .build();");

        JavaTypeModel type = new JavaTypeModel(
                JavaTypeModel.Kind.CLASS,
                context.layout().packageName(JavaLayout.CONFIG),
                TYPE_NAME,
                JavaVisibility.PUBLIC,
                Set.of(),
                Optional.of("Access declared by each endpoint, as one filter chain."),
                List.of(JavaAnnotationModel.marker(
                        "org.springframework.context.annotation.Configuration")),
                List.of(
                        new JavaImportModel("org.springframework.context.annotation.Bean"),
                        new JavaImportModel(
                                "org.springframework.security.config.http.SessionCreationPolicy"),
                        new JavaImportModel("org.springframework.http.HttpMethod"),
                        new JavaImportModel("org.springframework.http.MediaType"),
                        new JavaImportModel(
                                context.layout().packageName(JavaLayout.ERROR) + ".ApiError")),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new JavaMethodModel(
                        "harpiaFilterChain",
                        JavaTypeRef.of("org.springframework.security.web.SecurityFilterChain"),
                        JavaVisibility.PUBLIC,
                        Set.of(),
                        List.of(JavaAnnotationModel.marker(
                                "org.springframework.context.annotation.Bean")),
                        List.of(
                                new JavaParameterModel(
                                        "http",
                                        JavaTypeRef.of(
                                                "org.springframework.security.config.annotation"
                                                        + ".web.builders.HttpSecurity")),
                                new JavaParameterModel(
                                        "json",
                                        JavaTypeRef.of(
                                                "com.fasterxml.jackson.databind.ObjectMapper"))),
                        statements,
                        // Building the chain is declared to throw, and Spring expects the bean
                        // method to say so rather than swallow it into something less specific.
                        List.of(JavaTypeRef.of("java.lang.Exception")),
                        Optional.empty(),
                        Optional.of(where)),
                        writer(context, where)),
                Optional.of(where));
        return List.of(new JavaSourceFile(
                JavaLayout.sourcePath(context.layout().packagePath(JavaLayout.CONFIG), TYPE_NAME),
                type,
                Optional.of(where)));
    }

    /**
     * Every declared operation, with the access it asked for.
     *
     * <p>Keyed by method as well as path, because two operations can share a path and ask for
     * different things: a public {@code GET /customers} beside an authenticated
     * {@code POST /customers}. Collapsing those into one rule would make the generated code
     * contradict the specification — whichever rule won, one of the two operations never agreed
     * to it.
     *
     * <p>Sorted, so two identical specifications produce the same chain. Two operations cannot
     * share a method and a path: that is already refused as a duplicate route.
     */
    private static List<Route> routes(JavaSpringContext context) {
        return context.application().entities().stream()
                .flatMap(entity -> entity.operations().stream())
                .flatMap(operation -> operation.endpoint().stream())
                .map(endpoint -> new Route(
                        endpoint.method().name(), endpoint.effectivePath(), endpoint.access()))
                .sorted(java.util.Comparator
                        .comparing(Route::path)
                        .thenComparing(Route::method))
                .toList();
    }

    private record Route(String method, String path, ApplicationOperation.Access access) {
    }

    /** Writes the shared error body, because a refusal is a failure like any other. */
    private static JavaMethodModel writer(JavaSpringContext context, SourceRef where) {
        return new JavaMethodModel(
                "write",
                JavaTypeRef.of("void"),
                JavaVisibility.PRIVATE,
                Set.of(dev.harpia.target.javaspring.model.JavaModifier.STATIC),
                List.of(),
                List.of(
                        new JavaParameterModel(
                                "json",
                                JavaTypeRef.of("com.fasterxml.jackson.databind.ObjectMapper")),
                        new JavaParameterModel(
                                "response",
                                JavaTypeRef.of("jakarta.servlet.http.HttpServletResponse")),
                        new JavaParameterModel("status", JavaTypeRef.of("int")),
                        new JavaParameterModel("error", JavaTypeRef.of("java.lang.String")),
                        new JavaParameterModel("message", JavaTypeRef.of("java.lang.String"))),
                List.of(
                        "response.setStatus(status);",
                        "response.setContentType(MediaType.APPLICATION_JSON_VALUE);",
                        "json.writeValue("
                                + "response.getOutputStream(), new ApiError(status, error, "
                                + "message));"),
                List.of(JavaTypeRef.of("java.io.IOException")),
                Optional.empty(),
                Optional.of(where));
    }

    /**
     * How an identity arrives.
     *
     * <p>Basic is what the framework gives for nothing; a token is what a deployment with an
     * issuer uses. The chain is otherwise identical, because which endpoints need an identity is
     * not affected by how one is proved.
     */
    private static String mechanism(JavaSpringContext context) {
        return context.application().capabilities()
                .providerOf(Capability.SECURITY)
                .filter(provider -> provider.value().equals("jwt"))
                .map(provider -> ".oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))")
                .orElse(".httpBasic(basic -> {})");
    }

    /**
     * The rule as Spring spells it.
     *
     * <p>A role is written {@code admin} and checked as {@code hasRole("ADMIN")}: the
     * specification names people the way it names everything else, and the upper-case authority is
     * the framework's spelling of the same thing.
     */
    private static String rule(ApplicationOperation.Access access) {
        return switch (access.kind()) {
            case PUBLIC -> "permitAll()";
            case AUTHENTICATED -> "authenticated()";
            case ROLE -> "hasAnyRole(" + access.roles().stream()
                    .map(role -> "\"" + role.toUpperCase(java.util.Locale.ROOT) + "\"")
                    .reduce((left, right) -> left + ", " + right)
                    .orElseThrow() + ")";
            // A scope is spelled by whoever issued the token, so it is not upper-cased the way a
            // role is: `SCOPE_` is the prefix Spring reads, and the rest is the issuer's word.
            case SCOPE -> "hasAnyAuthority(" + access.roles().stream()
                    .map(scope -> "\"SCOPE_" + scope + "\"")
                    .reduce((left, right) -> left + ", " + right)
                    .orElseThrow() + ")";
        };
    }

    /**
     * The stricter of two rules for one path.
     *
     * <p>A rule that let one operation through would be a rule the other never agreed to, so the
     * narrower one wins: a role over merely authenticated, and either over public.
     */
    private static ApplicationOperation.Access stricter(
            ApplicationOperation.Access left, ApplicationOperation.Access right) {
        if (left.equals(right)) {
            return left;
        }
        if (left.kind() == ApplicationOperation.Access.Kind.ROLE) {
            return left;
        }
        if (right.kind() == ApplicationOperation.Access.Kind.ROLE) {
            return right;
        }
        return left.requiresIdentity() ? left : right;
    }
}
