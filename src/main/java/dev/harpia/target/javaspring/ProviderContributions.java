package dev.harpia.target.javaspring;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Merged provider contributions with conflicts rejected before emission. */
public record ProviderContributions(
        List<MavenDependency> dependencies,
        List<ConfigurationProperty> properties,
        List<ConfigurationProperty> testProperties) {

    public ProviderContributions {
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(testProperties, "testProperties");
        dependencies = dependencies.stream().sorted().toList();
        properties = properties.stream().sorted().toList();
        testProperties = testProperties.stream().sorted().toList();
    }

    public static ProviderContributions merge(Collection<ProviderContribution> contributions) {
        Objects.requireNonNull(contributions, "contributions");
        TreeMap<String, MavenDependency> dependencies = new TreeMap<>();
        TreeMap<String, ConfigurationProperty> properties = new TreeMap<>();
        TreeMap<String, ConfigurationProperty> testProperties = new TreeMap<>();
        for (ProviderContribution contribution : contributions) {
            for (MavenDependency dependency : contribution.dependencies()) {
                String key = dependency.groupId() + ":" + dependency.artifactId();
                MavenDependency previous = dependencies.putIfAbsent(key, dependency);
                if (previous != null && !previous.equals(dependency)) {
                    throw new IllegalArgumentException(
                            "providers contributed conflicting Maven dependency '" + key + "'");
                }
            }
            for (ConfigurationProperty property : contribution.properties()) {
                ConfigurationProperty previous = properties.putIfAbsent(property.key(), property);
                if (previous != null && !previous.equals(property)) {
                    throw new IllegalArgumentException(
                            "providers contributed conflicting property '" + property.key() + "'");
                }
            }
            for (ConfigurationProperty property : contribution.testProperties()) {
                ConfigurationProperty previous =
                        testProperties.putIfAbsent(property.key(), property);
                if (previous != null && !previous.equals(property)) {
                    throw new IllegalArgumentException(
                            "providers contributed conflicting test property '"
                                    + property.key() + "'");
                }
            }
        }
        return new ProviderContributions(
                dependencies.values().stream().toList(),
                properties.values().stream().toList(),
                testProperties.values().stream().toList());
    }
}
