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
        Map<String, ApplicationOperation.Access> routes = routes(context);
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
        routes.forEach((pattern, access) -> statements.add(
                "                .requestMatchers(\"" + pattern + "\")."
                        + (access == ApplicationOperation.Access.AUTHENTICATED
                                ? "authenticated()"
                                : "permitAll()")));
        statements.add("                .anyRequest().denyAll())");
        statements.add("        .httpBasic(basic -> {})");
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
                                "org.springframework.security.config.http.SessionCreationPolicy")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new JavaMethodModel(
                        "harpiaFilterChain",
                        JavaTypeRef.of("org.springframework.security.web.SecurityFilterChain"),
                        JavaVisibility.PUBLIC,
                        Set.of(),
                        List.of(JavaAnnotationModel.marker(
                                "org.springframework.context.annotation.Bean")),
                        List.of(new JavaParameterModel(
                                "http",
                                JavaTypeRef.of("org.springframework.security.config.annotation"
                                        + ".web.builders.HttpSecurity"))),
                        statements,
                        // Building the chain is declared to throw, and Spring expects the bean
                        // method to say so rather than swallow it into something less specific.
                        List.of(JavaTypeRef.of("java.lang.Exception")),
                        Optional.empty(),
                        Optional.of(where))),
                Optional.of(where));
        return List.of(new JavaSourceFile(
                JavaLayout.sourcePath(context.layout().packagePath(JavaLayout.CONFIG), TYPE_NAME),
                type,
                Optional.of(where)));
    }

    /**
     * Every declared route and the access it asked for.
     *
     * <p>Sorted, so two identical specifications produce the same chain. A path that two
     * operations share keeps the stricter of the two: a rule that let one of them through would
     * be a rule the other never agreed to.
     */
    private static Map<String, ApplicationOperation.Access> routes(JavaSpringContext context) {
        Map<String, ApplicationOperation.Access> routes = new TreeMap<>();
        for (ApplicationEntity entity : context.application().entities()) {
            for (ApplicationOperation operation : entity.operations()) {
                operation.endpoint().ifPresent(endpoint -> routes.merge(
                        endpoint.effectivePath(),
                        endpoint.access(),
                        JavaSpringSecurityTransformer::stricter));
            }
        }
        return new LinkedHashMap<>(routes);
    }

    private static ApplicationOperation.Access stricter(
            ApplicationOperation.Access left, ApplicationOperation.Access right) {
        return left == ApplicationOperation.Access.AUTHENTICATED
                        || right == ApplicationOperation.Access.AUTHENTICATED
                ? ApplicationOperation.Access.AUTHENTICATED
                : ApplicationOperation.Access.PUBLIC;
    }
}
