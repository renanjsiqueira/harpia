package dev.harpia.target.javaspring;

import dev.harpia.application.ApplicationProject;
import dev.harpia.capability.Capability;
import dev.harpia.capability.ResolvedCapability;
import dev.harpia.target.TargetConfiguration;
import java.util.ArrayList;
import java.util.List;

/** Resolves logical capabilities/providers into Java/Spring dependencies and properties. */
public final class JavaSpringDependencyResolver {

    private JavaSpringDependencyResolver() {
    }

    public static ProviderContributions resolve(
            ApplicationProject application, TargetConfiguration configuration) {
        List<ProviderContribution> contributions = new ArrayList<>();
        // The target always emits a Spring Boot entry point, so it always owes the dependency that
        // makes that entry point compile — even for a project whose only content is pure Logic.
        contributions.add(new ProviderContribution(
                List.of(MavenDependency.managed("org.springframework.boot", "spring-boot-starter")),
                List.of()));
        if (!application.entities().isEmpty()) {
            contributions.add(new ProviderContribution(
                    List.of(MavenDependency.managed(
                            "org.springframework.boot", "spring-boot-starter-validation")),
                    List.of()));
        }
        for (ResolvedCapability resolved : application.capabilities().asMap().values()) {
            contributions.add(contributionFor(resolved.capability(), application));
        }
        contributions.add(integrationCredentials(application));
        if (application.settings().generation().tests()) {
            contributions.add(new ProviderContribution(
                    List.of(MavenDependency.managed(
                            "org.springframework.boot", "spring-boot-starter-test", "test")),
                    List.of()));
        }
        return ProviderContributions.merge(contributions);
    }

    private static ProviderContribution contributionFor(
            Capability capability, ApplicationProject application) {
        return switch (capability) {
            case HTTP -> new ProviderContribution(
                    List.of(
                            MavenDependency.managed(
                                    "org.springframework.boot", "spring-boot-starter-validation"),
                            MavenDependency.managed(
                                    "org.springframework.boot", "spring-boot-starter-web")),
                    List.of());
            case PERSISTENCE -> new ProviderContribution(
                    persistenceDependencies(application),
                    List.of(
                            new ConfigurationProperty(
                                    "spring.datasource.password", "${DATABASE_PASSWORD}"),
                            new ConfigurationProperty("spring.datasource.url", "${DATABASE_URL}"),
                            new ConfigurationProperty(
                                    "spring.datasource.username", "${DATABASE_USERNAME}"),
                            new ConfigurationProperty(
                                    "spring.flyway.enabled",
                                    Boolean.toString(application.settings().generation().migrations())),
                            new ConfigurationProperty("spring.jpa.hibernate.ddl-auto", "validate"),
                            new ConfigurationProperty("spring.jpa.open-in-view", "false")));
            case SECURITY -> securityContribution(application);
            case EVENTS, CUSTOM -> ProviderContribution.empty();
        };
    }

    /**
     * What a call needs in order to prove who is calling.
     *
     * <p>The binding says which scheme reaches the other side; the value is a secret that differs
     * per deployment, so it is a placeholder here and never a line in a specification anyone
     * commits. A test has no deployment to fill the placeholder in, and the context reads the
     * property on the way up, so the tests get a value of their own — one that is obviously not a
     * credential, because nothing generated should look like it shipped with one.
     */
    private static ProviderContribution integrationCredentials(ApplicationProject application) {
        List<ConfigurationProperty> properties = new ArrayList<>();
        List<ConfigurationProperty> testProperties = new ArrayList<>();
        for (dev.harpia.application.ApplicationIntegration integration
                : application.integrations()) {
            boolean authenticated = integration.operations().stream()
                    .flatMap(operation -> operation.http().stream())
                    .anyMatch(http -> http.auth().isPresent());
            if (!authenticated) {
                continue;
            }
            String name = dev.harpia.target.javaspring.transformer
                    .JavaSpringIntegrationClientTransformer.propertyName(integration.name());
            String key = "harpia.integration." + name + ".credential";
            properties.add(new ConfigurationProperty(
                    key,
                    "${" + name.replace('-', '_').toUpperCase(java.util.Locale.ROOT)
                            + "_CREDENTIAL}"));
            testProperties.add(new ConfigurationProperty(key, "harpia-test-credential"));
        }
        return new ProviderContribution(List.of(), properties, testProperties);
    }

    /**
     * What proving an identity costs the build.
     *
     * <p>A token is validated against a key set the deployment names, so the property is a
     * placeholder here: a URL baked into generated code would be one environment's answer written
     * into every environment's source.
     */
    private static ProviderContribution securityContribution(ApplicationProject application) {
        List<MavenDependency> dependencies = new ArrayList<>();
        dependencies.add(MavenDependency.managed(
                "org.springframework.boot", "spring-boot-starter-security"));
        boolean jwt = application.capabilities()
                .providerOf(dev.harpia.capability.Capability.SECURITY)
                .filter(provider -> provider.value().equals("jwt"))
                .isPresent();
        if (!jwt) {
            return new ProviderContribution(dependencies, List.of());
        }
        dependencies.add(MavenDependency.managed(
                "org.springframework.boot", "spring-boot-starter-oauth2-resource-server"));
        return new ProviderContribution(
                dependencies,
                List.of(new ConfigurationProperty(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "${JWT_JWK_SET_URI}")),
                // The context reads this on the way up, and a test has no deployment to fill the
                // placeholder in. The address is never fetched: no generated test presents a token.
                List.of(new ConfigurationProperty(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "http://localhost/.harpia-no-issuer/jwks.json")));
    }

    private static List<MavenDependency> persistenceDependencies(ApplicationProject application) {
        List<MavenDependency> dependencies = new ArrayList<>();
        dependencies.add(MavenDependency.managed(
                "org.springframework.boot", "spring-boot-starter-data-jpa"));
        dependencies.add(MavenDependency.managed("org.postgresql", "postgresql", "runtime"));
        if (application.settings().generation().migrations()) {
            dependencies.add(MavenDependency.managed("org.flywaydb", "flyway-core"));
            dependencies.add(MavenDependency.managed(
                    "org.flywaydb", "flyway-database-postgresql"));
        }
        return dependencies;
    }
}
