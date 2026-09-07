package dev.harpia.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harpia.capability.Capability;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TargetCatalogTest {

    @Test
    void javaSpringIsTheOnlySupportedTarget() {
        assertThat(TargetCatalog.supported())
                .containsExactly(TargetCatalog.JAVA_SPRING);
    }

    @Test
    void everyFutureTargetIsCataloguedAsNotSupported() {
        assertThat(TargetCatalog.planned())
                .extracting(TargetId::value)
                .containsExactly(
                        "clojure-jvm",
                        "csharp-aspnet",
                        "elixir-phoenix",
                        "go",
                        "kotlin-spring",
                        "php-laravel",
                        "python-fastapi",
                        "ruby-rails",
                        "rust",
                        "typescript-nestjs");
        assertThat(TargetCatalog.all())
                .filteredOn(descriptor -> !descriptor.status().canGenerate())
                .allSatisfy(descriptor -> {
                    assertThat(descriptor.status()).isEqualTo(TargetStatus.NOT_SUPPORTED);
                    assertThat(descriptor.capabilities()).isEmpty();
                });
    }

    @Test
    void aTargetWithoutAGeneratorCannotClaimCapabilities() {
        assertThatThrownBy(() -> new TargetDescriptor(
                        TargetId.of("csharp-aspnet"),
                        "C# + ASP.NET Core",
                        "csharp",
                        "aspnet",
                        TargetStatus.NOT_SUPPORTED,
                        "C# >= 12",
                        12,
                        Set.of(Capability.HTTP)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not declare capabilities");
    }

    @Test
    void aTargetIsALanguageAndAFrameworkTogether() {
        TargetDescriptor java = TargetCatalog.find(TargetCatalog.JAVA_SPRING).orElseThrow();

        assertThat(java.language()).isEqualTo("java");
        assertThat(java.framework()).isEqualTo("spring-boot");
        assertThat(java.targetVersion()).isEqualTo("1");
        assertThat(java.templateSet()).isEqualTo("default");
        assertThat(java.templateVersion()).isEqualTo(1);
        assertThat(TargetCatalog.find(TargetId.of("kotlin-spring")).orElseThrow().language())
                .isEqualTo("kotlin");
    }

    @Test
    void targetIdentifiersAreLowercaseAndDashSeparated() {
        assertThatThrownBy(() -> TargetId.of("Java_Spring"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TargetId.of("typescript-nestjs").value()).isEqualTo("typescript-nestjs");
    }

    @Test
    void exposesStableDiscoveryDtosWithoutGeneratorClasses() {
        TargetInfo java = TargetCatalog.info().getFirst();

        assertThat(java.id()).isEqualTo("java-spring");
        assertThat(java.status()).isEqualTo(TargetStatus.SUPPORTED);
        assertThat(java.capabilities())
                .extracting(TargetCapabilityInfo::id)
                .containsExactly("http", "persistence");
        assertThat(TargetCatalog.info())
                .filteredOn(info -> info.status() == TargetStatus.NOT_SUPPORTED)
                .allSatisfy(info -> assertThat(info.capabilities()).isEmpty());
    }
}
