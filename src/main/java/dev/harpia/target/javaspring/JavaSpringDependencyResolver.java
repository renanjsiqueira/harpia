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
            case SECURITY -> new ProviderContribution(
                    List.of(MavenDependency.managed(
                            "org.springframework.boot", "spring-boot-starter-security")),
                    List.of());
            case EVENTS, CUSTOM -> ProviderContribution.empty();
        };
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
