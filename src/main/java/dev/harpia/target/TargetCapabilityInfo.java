package dev.harpia.target;

import java.util.Objects;

/** Stable discovery DTO; it exposes no generator implementation class. */
public record TargetCapabilityInfo(String id) implements Comparable<TargetCapabilityInfo> {

    public TargetCapabilityInfo {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public int compareTo(TargetCapabilityInfo other) {
        return id.compareTo(other.id);
    }
}
