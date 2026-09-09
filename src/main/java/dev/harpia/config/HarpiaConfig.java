package dev.harpia.config;

import dev.harpia.LanguageVersion;
import java.util.Objects;

/** Fully validated V0 project configuration. */
public record HarpiaConfig(
        HarpiaSettings harpia,
        ProjectConfig project,
        TargetConfig target,
        DatabaseConfig database,
        PathsConfig paths,
        GenerationConfig generation) {

    public HarpiaConfig {
        Objects.requireNonNull(harpia, "harpia");
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(generation, "generation");
    }

    /** Versions owned by Harpia itself, independent from any generation target. */
    public record HarpiaSettings(int schemaVersion, LanguageVersion languageVersion) {
        public HarpiaSettings {
            Objects.requireNonNull(languageVersion, "languageVersion");
        }
    }

    public record ProjectConfig(String name, String group, String artifact, String packageName) {
        public ProjectConfig {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(packageName, "packageName");
        }
    }

    /**
     * The selected target and its options.
     *
     * <p>{@code id} names a target from the catalogue; {@code languageVersion} is the version of
     * that target's language. {@code options} carries values only the target understands, such as
     * {@code springBootVersion}: the compiler core never interprets them.
     */
    public record TargetConfig(
            String id, int languageVersion, java.util.Map<String, String> options) {
        public TargetConfig {
            Objects.requireNonNull(id, "id");
            options = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(Objects.requireNonNull(options, "options")));
        }
    }

    public record DatabaseConfig(String vendor) {
        public DatabaseConfig {
            Objects.requireNonNull(vendor, "vendor");
        }
    }

    public record PathsConfig(String specs, String bindings, String output) {
        public PathsConfig {
            Objects.requireNonNull(specs, "specs");
            Objects.requireNonNull(bindings, "bindings");
            Objects.requireNonNull(output, "output");
        }
    }

    public record GenerationConfig(boolean migrations, boolean tests) {
    }
}
