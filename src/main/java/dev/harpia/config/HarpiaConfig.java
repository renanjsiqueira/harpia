package dev.harpia.config;

import dev.harpia.LanguageVersion;
import java.util.Objects;

/** Fully validated V0 project configuration. */
public record HarpiaConfig(
        HarpiaSettings harpia,
        ProjectConfig project,
        TargetConfig target,
        DatabaseConfig database,
        SecurityConfig security,
        PathsConfig paths,
        GenerationConfig generation) {

    /**
     * The shape from before an identity could be proved more than one way.
     *
     * <p>It defaults to `basic`, which is what a project that never mentioned security was already
     * getting, so nothing that compiled stops compiling and nothing changes meaning.
     */
    public HarpiaConfig(
            HarpiaSettings harpia,
            ProjectConfig project,
            TargetConfig target,
            DatabaseConfig database,
            PathsConfig paths,
            GenerationConfig generation) {
        this(harpia, project, target, database,
                new SecurityConfig(SecurityConfig.BASIC), paths, generation);
    }

    public HarpiaConfig {
        Objects.requireNonNull(harpia, "harpia");
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(security, "security");
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
            String id,
            int languageVersion,
            java.util.Map<String, String> options,
            java.util.Map<String, String> properties) {

        public TargetConfig(String id, int languageVersion, java.util.Map<String, String> options) {
            this(id, languageVersion, options, java.util.Map.of());
        }

        public TargetConfig {
            Objects.requireNonNull(id, "id");
            options = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(Objects.requireNonNull(options, "options")));
            // Sorted, because a configuration file is read by people and diffed by machines, and
            // neither is served by the order someone happened to type the keys in.
            properties = java.util.Collections.unmodifiableMap(
                    new java.util.TreeMap<>(Objects.requireNonNull(properties, "properties")));
        }
    }

    public record DatabaseConfig(String vendor) {
        public DatabaseConfig {
            Objects.requireNonNull(vendor, "vendor");
        }
    }

    /**
     * How an identity is proved.
     *
     * <p>A logical provider, like the database vendor: `basic` and `jwt` are both ways of arriving
     * with an identity, and which one a deployment uses is not something the specification said.
     * It defaults to `basic`, which is what a framework gives for nothing.
     */
    public record SecurityConfig(String provider) {
        public static final String BASIC = "basic";
        public static final String JWT = "jwt";

        public SecurityConfig {
            Objects.requireNonNull(provider, "provider");
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
