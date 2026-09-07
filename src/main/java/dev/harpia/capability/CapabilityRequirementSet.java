package dev.harpia.capability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, deterministically ordered capability requirements derived from Business IR. */
public final class CapabilityRequirementSet {

    private static final Comparator<CapabilityRequirement> REQUIREMENT_ORDER =
            Comparator.comparing((CapabilityRequirement item) -> item.where().file())
                    .thenComparingInt(item -> item.where().line())
                    .thenComparingInt(item -> item.where().column())
                    .thenComparing(CapabilityRequirement::reason);

    private final Map<Capability, List<CapabilityRequirement>> requirements;

    public CapabilityRequirementSet(Collection<CapabilityRequirement> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        EnumMap<Capability, List<CapabilityRequirement>> grouped = new EnumMap<>(Capability.class);
        for (CapabilityRequirement requirement : requirements) {
            CapabilityRequirement item = Objects.requireNonNull(requirement, "requirement");
            grouped.computeIfAbsent(item.capability(), ignored -> new ArrayList<>()).add(item);
        }
        EnumMap<Capability, List<CapabilityRequirement>> immutable = new EnumMap<>(Capability.class);
        grouped.forEach((capability, values) -> immutable.put(
                capability,
                values.stream().sorted(REQUIREMENT_ORDER).toList()));
        this.requirements = Collections.unmodifiableMap(immutable);
    }

    public boolean requires(Capability capability) {
        return requirements.containsKey(capability);
    }

    public List<CapabilityRequirement> forCapability(Capability capability) {
        return requirements.getOrDefault(capability, List.of());
    }

    public Map<Capability, List<CapabilityRequirement>> asMap() {
        return requirements;
    }
}
