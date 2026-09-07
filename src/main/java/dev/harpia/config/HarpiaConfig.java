package dev.harpia.config;

import java.util.Objects;

/** Fully validated V0 project configuration. */
public record HarpiaConfig(
        int harpia,
        ProjectConfig project,
        TargetConfig target,
        DatabaseConfig database,
        PathsConfig paths,
        GenerationConfig generation) {

    public HarpiaConfig {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(generation, "generation");
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

    public record PathsConfig(String specs, String output) {
        public PathsConfig {
            Objects.requireNonNull(specs, "specs");
            Objects.requireNonNull(output, "output");
        }
    }

    public record GenerationConfig(boolean migrations, boolean tests) {
    }
}
