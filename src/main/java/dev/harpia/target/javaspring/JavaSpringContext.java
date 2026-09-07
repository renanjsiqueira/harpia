package dev.harpia.target.javaspring;

import dev.harpia.application.ApplicationProject;
import dev.harpia.target.TargetConfiguration;
import java.util.Objects;

/** Everything a Java/Spring emitter is allowed to see. */
public record JavaSpringContext(
        ApplicationProject application,
        TargetConfiguration configuration,
        JavaLayout layout,
        ProviderContributions contributions) {

    public JavaSpringContext {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(contributions, "contributions");
    }

    public static JavaSpringContext of(
            ApplicationProject application, TargetConfiguration configuration) {
        return new JavaSpringContext(
                application,
                configuration,
                JavaLayout.of(
                        application.settings().namespace(), application.settings().name()),
                JavaSpringDependencyResolver.resolve(application, configuration));
    }
}
