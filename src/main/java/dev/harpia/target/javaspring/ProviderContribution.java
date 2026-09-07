package dev.harpia.target.javaspring;

import java.util.List;
import java.util.Objects;

/** Deterministic build and configuration inputs contributed by one provider. */
public record ProviderContribution(
        List<MavenDependency> dependencies,
        List<ConfigurationProperty> properties) {

    public ProviderContribution {
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(properties, "properties");
        dependencies = dependencies.stream().sorted().distinct().toList();
        properties = properties.stream().sorted().distinct().toList();
    }

    public static ProviderContribution empty() {
        return new ProviderContribution(List.of(), List.of());
    }
}
