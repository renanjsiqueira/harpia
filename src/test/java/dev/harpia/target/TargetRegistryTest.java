package dev.harpia.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harpia.application.ApplicationProject;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.target.TargetGenerationResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TargetRegistryTest {

    @Test
    void theBuiltInRegistryShipsOnlyJavaSpring() {
        TargetRegistry registry = TargetRegistry.standard();

        assertThat(registry.ids()).containsExactly(TargetCatalog.JAVA_SPRING);
        assertThat(registry.find(TargetCatalog.JAVA_SPRING)).isPresent();
        assertThat(registry.find(TargetId.of("csharp-aspnet"))).isEmpty();
    }

    @Test
    void aCataloguedTargetWithoutAGeneratorHasNoStubImplementation() {
        assertThat(TargetCatalog.find(TargetId.of("csharp-aspnet"))).isPresent();
        assertThat(TargetRegistry.standard().find(TargetId.of("csharp-aspnet"))).isEmpty();
    }

    @Test
    void aRegistryCanBeAssembledWithoutTouchingTheCompilerCore() {
        TargetRegistry registry = new TargetRegistry(List.of(new FakeTarget()));

        assertThat(registry.ids()).containsExactly(TargetId.of("go"));
    }

    @Test
    void aTargetThatCannotGenerateIsRejectedByTheRegistry() {
        assertThatThrownBy(() -> new TargetRegistry(List.of(new UnavailableTarget())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be able to generate");
    }

    private static final class FakeTarget implements HarpiaTarget {

        @Override
        public TargetDescriptor descriptor() {
            return new TargetDescriptor(
                    TargetId.of("go"), "Go", "go", "net/http",
                    TargetStatus.EXPERIMENTAL, "Go >= 1.22", 1, Set.of());
        }

        @Override
        public void validate(
                ApplicationProject application,
                TargetConfiguration configuration,
                DiagnosticCollector diagnostics) {
        }

        @Override
        public TargetGenerationResult generate(
                ApplicationProject application, TargetConfiguration configuration) {
            return new TargetGenerationResult(List.of());
        }
    }

    private static final class UnavailableTarget extends Object implements HarpiaTarget {

        @Override
        public TargetDescriptor descriptor() {
            return TargetDescriptor.planned("rust", "Rust", "rust", "axum");
        }

        @Override
        public void validate(
                ApplicationProject application,
                TargetConfiguration configuration,
                DiagnosticCollector diagnostics) {
        }

        @Override
        public TargetGenerationResult generate(
                ApplicationProject application, TargetConfiguration configuration) {
            return new TargetGenerationResult(List.of());
        }
    }
}
