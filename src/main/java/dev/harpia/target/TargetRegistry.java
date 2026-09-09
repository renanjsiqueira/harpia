package dev.harpia.target;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps target identifiers to the generators this compiler actually ships.
 *
 * <p>The registry is an instance rather than a static switch so that a future plugin loader can
 * assemble a different set of targets without touching the compiler core. Resolution is a lookup,
 * never a {@code switch} on language spread across the pipeline.
 *
 * <p>A registered target carries its own {@link TargetDescriptor}, so the registry — not
 * {@link TargetCatalog} — is the authority on what this compiler can generate. The catalogue
 * describes targets that are only planned, which is a different question.
 */
public final class TargetRegistry {

    private final Map<TargetId, HarpiaTarget> targets;

    public TargetRegistry(List<HarpiaTarget> targets) {
        Objects.requireNonNull(targets, "targets");
        Map<TargetId, HarpiaTarget> indexed = new LinkedHashMap<>();
        for (HarpiaTarget target : targets) {
            TargetDescriptor descriptor = target.descriptor();
            if (!descriptor.status().canGenerate()) {
                throw new IllegalArgumentException(
                        "a registered target must be able to generate: " + descriptor.id());
            }
            if (indexed.putIfAbsent(descriptor.id(), target) != null) {
                throw new IllegalArgumentException("duplicate target: " + descriptor.id());
            }
        }
        this.targets = Map.copyOf(indexed);
    }

    /** The built-in registry. Java/Spring is the only target with a generator today. */
    public static TargetRegistry standard() {
        return new TargetRegistry(
                List.of(new dev.harpia.target.javaspring.JavaSpringTarget()));
    }

    public Optional<HarpiaTarget> find(TargetId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(targets.get(id));
    }

    public List<TargetId> ids() {
        return targets.keySet().stream().sorted().toList();
    }

    /** The descriptor a registered target declares about itself. */
    public Optional<TargetDescriptor> descriptor(TargetId id) {
        return find(id).map(HarpiaTarget::descriptor);
    }
}
