package dev.harpia.application;

import dev.harpia.config.HarpiaConfig;
import java.util.Objects;

/**
 * Target-independent project settings.
 *
 * <p>{@code namespace} is the logical grouping of the generated application: a package for Java, a
 * namespace for C#, a module path for TypeScript. Build coordinates, language version and any
 * framework option live in the target configuration instead, because they are not properties of the
 * application, only of how one target builds it.
 */
public record ProjectSettings(
        String name,
        String namespace,
        Generation generation) {

    public ProjectSettings {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(generation, "generation");
    }

    public static ProjectSettings from(HarpiaConfig config) {
        Objects.requireNonNull(config, "config");
        return new ProjectSettings(
                config.project().name(),
                config.project().packageName(),
                new Generation(
                        config.generation().migrations(),
                        config.generation().tests()));
    }

    public record Generation(boolean migrations, boolean tests) {
    }
}
