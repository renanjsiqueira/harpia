package dev.harpia.target.javaspring;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic build and configuration inputs contributed by one provider.
 *
 * <p>{@code testProperties} are what the generated tests need to start. A production property is
 * usually a placeholder the deployment fills in, and a test has no deployment: when the context
 * reads that property on the way up, the tests need a value of their own rather than a fabricated
 * default written into the configuration everyone ships.
 */
public record ProviderContribution(
        List<MavenDependency> dependencies,
        List<ConfigurationProperty> properties,
        List<ConfigurationProperty> testProperties) {

    public ProviderContribution(
            List<MavenDependency> dependencies, List<ConfigurationProperty> properties) {
        this(dependencies, properties, List.of());
    }

    public ProviderContribution {
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(testProperties, "testProperties");
        dependencies = dependencies.stream().sorted().distinct().toList();
        properties = properties.stream().sorted().distinct().toList();
        testProperties = testProperties.stream().sorted().distinct().toList();
    }

    public static ProviderContribution empty() {
        return new ProviderContribution(List.of(), List.of(), List.of());
    }
}
