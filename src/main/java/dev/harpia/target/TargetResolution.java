package dev.harpia.target;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of target resolution: always a descriptor, and a generator only when this compiler
 * ships one for that identifier.
 */
public record TargetResolution(
        TargetDescriptor descriptor,
        Optional<HarpiaTarget> generator,
        TargetConfiguration configuration) {

    public TargetResolution {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(configuration, "configuration");
        if (generator.isPresent()
                && !generator.orElseThrow().descriptor().id().equals(descriptor.id())) {
            throw new IllegalArgumentException("generator does not match descriptor");
        }
    }

    public boolean canGenerate() {
        return generator.isPresent();
    }
}
