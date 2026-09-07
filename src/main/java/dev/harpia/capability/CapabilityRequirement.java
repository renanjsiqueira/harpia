package dev.harpia.capability;

import dev.harpia.diag.SourceRef;
import java.util.Objects;

/** One source-backed reason why the application needs a capability. */
public record CapabilityRequirement(Capability capability, String reason, SourceRef where) {
    public CapabilityRequirement {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(where, "where");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
