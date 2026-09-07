package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderContributionsTest {

    @Test
    void mergesAndOrdersProviderInputsDeterministically() {
        ProviderContribution later = new ProviderContribution(
                List.of(MavenDependency.managed("z.group", "z-artifact")),
                List.of(new ConfigurationProperty("z.value", "last")));
        ProviderContribution earlier = new ProviderContribution(
                List.of(MavenDependency.managed("a.group", "a-artifact")),
                List.of(new ConfigurationProperty("a.value", "first")));

        ProviderContributions merged = ProviderContributions.merge(List.of(later, earlier));

        assertThat(merged.dependencies())
                .extracting(MavenDependency::artifactId)
                .containsExactly("a-artifact", "z-artifact");
        assertThat(merged.properties())
                .extracting(ConfigurationProperty::key)
                .containsExactly("a.value", "z.value");
    }

    @Test
    void rejectsConflictingContributionsBeforeEmission() {
        ProviderContribution first = new ProviderContribution(
                List.of(MavenDependency.managed("example", "library")),
                List.of(new ConfigurationProperty("example.value", "one")));
        ProviderContribution second = new ProviderContribution(
                List.of(MavenDependency.managed("example", "library", "runtime")),
                List.of(new ConfigurationProperty("example.value", "two")));

        assertThatThrownBy(() -> ProviderContributions.merge(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maven dependency 'example:library'");
    }
}
