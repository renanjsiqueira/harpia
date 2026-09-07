package dev.harpia.capability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CustomerFixture;
import dev.harpia.config.HarpiaConfig;
import dev.harpia.diag.ErrorCodes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Capability inference and provider selection are target independent, so nothing here mentions
 * Java, Spring, Maven or JPA. Those assertions belong to the Java/Spring target's own tests.
 */
class CapabilityResolverTest {

    @Test
    void infersHttpAndPersistenceFromTheBusinessIrAlone() {
        CustomerFixture fixture = fixture();

        CapabilityRequirementSet requirements = CapabilityAnalyzer.analyze(fixture.business());

        assertThat(requirements.asMap().keySet())
                .containsExactly(Capability.HTTP, Capability.PERSISTENCE);
        assertThat(requirements.forCapability(Capability.HTTP)).hasSize(5);
        assertThat(requirements.forCapability(Capability.PERSISTENCE)).isNotEmpty();
    }

    @Test
    void selectsALogicalProviderOnlyWhereTheUserActuallyChoosesOne() {
        CustomerFixture fixture = fixture();

        ResolvedCapabilities resolved = CapabilityResolver.resolve(
                        CapabilityAnalyzer.analyze(fixture.business()),
                        fixture.config(),
                        fixture.diagnostics())
                .orElseThrow();

        assertThat(fixture.diagnostics().diagnostics()).isEmpty();
        assertThat(resolved.providerOf(Capability.PERSISTENCE))
                .contains(CapabilityResolver.POSTGRESQL);
        assertThat(resolved.requires(Capability.HTTP)).isTrue();
        assertThat(resolved.providerOf(Capability.HTTP))
                .as("the target exposes endpoints itself; there is no HTTP provider to choose")
                .isEmpty();
        assertThat(resolved.get(Capability.EVENTS)).isEmpty();
    }

    @Test
    void reportsAProviderMismatchAtTheRequestingSource() {
        CustomerFixture fixture = fixture();
        HarpiaConfig invalid = new HarpiaConfig(
                fixture.config().harpia(),
                fixture.config().project(),
                fixture.config().target(),
                new HarpiaConfig.DatabaseConfig("other"),
                fixture.config().paths(),
                fixture.config().generation());

        Optional<ResolvedCapabilities> resolved = CapabilityResolver.resolve(
                CapabilityAnalyzer.analyze(fixture.business()), invalid, fixture.diagnostics());

        assertThat(resolved).isEmpty();
        assertThat(fixture.diagnostics().diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code())
                            .isEqualTo(ErrorCodes.CAPABILITY_PROVIDER_UNSUPPORTED);
                    assertThat(diagnostic.where()).hasValueSatisfying(where ->
                            assertThat(where.file()).isEqualTo("specs/customer.harpia.md"));
                });
    }

    @Test
    void requirementOrderingIsIndependentFromInputOrder() {
        CapabilityRequirement later = new CapabilityRequirement(
                Capability.HTTP,
                "later",
                dev.harpia.diag.SourceRef.of("specs/z.harpia.md", 4, 1));
        CapabilityRequirement earlier = new CapabilityRequirement(
                Capability.HTTP,
                "earlier",
                dev.harpia.diag.SourceRef.of("specs/a.harpia.md", 8, 1));

        CapabilityRequirementSet requirements =
                new CapabilityRequirementSet(List.of(later, earlier));

        assertThat(requirements.forCapability(Capability.HTTP))
                .extracting(item -> item.where().file())
                .containsExactly("specs/a.harpia.md", "specs/z.harpia.md");
    }

    private static CustomerFixture fixture() {
        return CustomerFixture.load();
    }
}
