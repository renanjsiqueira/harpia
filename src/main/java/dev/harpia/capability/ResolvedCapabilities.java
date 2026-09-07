package dev.harpia.capability;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable provider selection for every required capability. */
public final class ResolvedCapabilities {

    private final Map<Capability, ResolvedCapability> capabilities;

    public ResolvedCapabilities(Collection<ResolvedCapability> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        EnumMap<Capability, ResolvedCapability> indexed = new EnumMap<>(Capability.class);
        for (ResolvedCapability capability : capabilities) {
            ResolvedCapability previous = indexed.putIfAbsent(
                    capability.capability(), Objects.requireNonNull(capability, "capability"));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "capability resolved more than once: " + capability.capability().id());
            }
        }
        this.capabilities = Collections.unmodifiableMap(indexed);
    }

    public Optional<ResolvedCapability> get(Capability capability) {
        return Optional.ofNullable(capabilities.get(capability));
    }

    public Map<Capability, ResolvedCapability> asMap() {
        return capabilities;
    }

    /** The logical provider selected for a capability, if the capability is required at all. */
    public Optional<ProviderId> providerOf(Capability capability) {
        return get(capability).flatMap(ResolvedCapability::provider);
    }

    public boolean requires(Capability capability) {
        return capabilities.containsKey(capability);
    }
}
