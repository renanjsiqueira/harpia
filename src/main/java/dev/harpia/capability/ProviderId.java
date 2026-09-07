package dev.harpia.capability;

import java.util.Objects;

/** Stable provider identifier kept out of Business IR. */
public record ProviderId(String value) implements Comparable<ProviderId> {
    public ProviderId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("invalid provider id: " + value);
        }
    }

    @Override
    public int compareTo(ProviderId other) {
        return value.compareTo(other.value);
    }
}
