package dev.harpia.capability;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One capability and the logical provider chosen for it.
 *
 * <p>The provider is empty when the target implements the capability itself. HTTP is such a case:
 * there is no user-selectable HTTP provider, the target simply knows how to expose endpoints.
 * Persistence is not: PostgreSQL is a logical provider that outlives any single target.
 *
 * <p>What a provider contributes to a build — dependencies, configuration keys, generated
 * adapters — is decided by the target, never here.
 */
public record ResolvedCapability(
        Capability capability,
        Optional<ProviderId> provider,
        List<CapabilityRequirement> requirements) {

    public ResolvedCapability {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(provider, "provider");
        requirements = List.copyOf(requirements);
        if (requirements.isEmpty()
                || requirements.stream().anyMatch(item -> item.capability() != capability)) {
            throw new IllegalArgumentException("requirements must belong to " + capability.id());
        }
    }
}
