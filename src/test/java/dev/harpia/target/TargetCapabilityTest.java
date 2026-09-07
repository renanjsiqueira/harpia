package dev.harpia.target;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.capability.Capability;
import dev.harpia.capability.CapabilityRequirement;
import dev.harpia.capability.CapabilityRequirementSet;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import org.junit.jupiter.api.Test;

/**
 * A capability is required by the specification and implemented by a target. When those two
 * disagree, the compiler says so before generating anything, instead of producing a project that
 * silently lacks the behaviour.
 */
class TargetCapabilityTest {

    private static final TargetDescriptor JAVA_SPRING =
            TargetCatalog.find(TargetCatalog.JAVA_SPRING).orElseThrow();

    @Test
    void acceptsRequirementsTheTargetImplements() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        boolean supported = TargetResolver.supportsEveryRequirement(
                JAVA_SPRING, requirements(Capability.HTTP, Capability.PERSISTENCE), diagnostics);

        assertThat(supported).isTrue();
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void rejectsARequirementTheTargetDoesNotImplementYet() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        boolean supported = TargetResolver.supportsEveryRequirement(
                JAVA_SPRING, requirements(Capability.EVENTS), diagnostics);

        assertThat(supported).isFalse();
        assertThat(diagnostics.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code())
                    .isEqualTo(ErrorCodes.TARGET_CAPABILITY_UNSUPPORTED);
            assertThat(diagnostic.message())
                    .contains("`java-spring`")
                    .contains("capability `events`");
            assertThat(diagnostic.where()).hasValueSatisfying(where ->
                    assertThat(where.file()).isEqualTo("specs/customer.harpia.md"));
        });
    }

    @Test
    void aTargetOnlyDeclaresCapabilitiesItsGeneratorActuallyImplements() {
        assertThat(JAVA_SPRING.supports(Capability.HTTP)).isTrue();
        assertThat(JAVA_SPRING.supports(Capability.PERSISTENCE)).isTrue();
        assertThat(JAVA_SPRING.supports(Capability.EVENTS))
                .as("events have no generator yet, so the target must not claim them")
                .isFalse();
    }

    private static CapabilityRequirementSet requirements(Capability... capabilities) {
        return new CapabilityRequirementSet(java.util.Arrays.stream(capabilities)
                .map(capability -> new CapabilityRequirement(
                        capability,
                        "required by the fixture",
                        SourceRef.of("specs/customer.harpia.md", 3, 1)))
                .toList());
    }
}
