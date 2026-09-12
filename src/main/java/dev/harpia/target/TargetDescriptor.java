package dev.harpia.target;

import dev.harpia.capability.Capability;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Metadata of a target, available whether or not a generator exists for it.
 *
 * <p>A target that cannot generate declares no capabilities. Capabilities describe what a generator
 * actually implements, never what someone believes could be implemented.
 */
public record TargetDescriptor(
        TargetId id,
        String displayName,
        String language,
        String framework,
        TargetStatus status,
        String languageRequirement,
        int minimumLanguageVersion,
        Set<Capability> capabilities,
        String targetVersion,
        String templateSet,
        int templateVersion,
        List<Gate> gates) {

    /**
     * A gate this target's toolchain owns, and the command that runs it.
     *
     * <p>Harpia runs {@code validate} and {@code build} itself and can report on them. It cannot
     * run these: they happen in the toolchain of the language it generated, which is exactly why
     * the target is the one that knows how. {@code {output}} stands for the generated directory.
     */
    public record Gate(String name, String command) {
        public Gate {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(command, "command");
        }

        /** The command as someone would type it for a project generated into {@code output}. */
        public String commandFor(String output) {
            return command.replace("{output}", output);
        }
    }

    public TargetDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(framework, "framework");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(languageRequirement, "languageRequirement");
        Objects.requireNonNull(targetVersion, "targetVersion");
        Objects.requireNonNull(templateSet, "templateSet");
        capabilities = Set.copyOf(capabilities);
        gates = List.copyOf(gates);
        if (!status.canGenerate() && !(capabilities.isEmpty() && gates.isEmpty())) {
            throw new IllegalArgumentException(
                    "a target without a generator must not declare capabilities or gates: " + id);
        }
        if (status.canGenerate() && (targetVersion.isBlank()
                || templateSet.isBlank()
                || templateVersion < 1)) {
            throw new IllegalArgumentException("a generator must version its target and templates: " + id);
        }
    }

    /**
     * The shape promised before targets declared their verification gates.
     *
     * <p>A registry built against it keeps compiling and gets a target that declares no gates,
     * which is the truth: it never said it had any.
     */
    public TargetDescriptor(
            TargetId id,
            String displayName,
            String language,
            String framework,
            TargetStatus status,
            String languageRequirement,
            int minimumLanguageVersion,
            Set<Capability> capabilities,
            String targetVersion,
            String templateSet,
            int templateVersion) {
        this(
                id,
                displayName,
                language,
                framework,
                status,
                languageRequirement,
                minimumLanguageVersion,
                capabilities,
                targetVersion,
                templateSet,
                templateVersion,
                List.of());
    }

    /** Compatibility constructor for registry implementations using the original target SPI. */
    public TargetDescriptor(
            TargetId id,
            String displayName,
            String language,
            String framework,
            TargetStatus status,
            String languageRequirement,
            int minimumLanguageVersion,
            Set<Capability> capabilities) {
        this(
                id,
                displayName,
                language,
                framework,
                status,
                languageRequirement,
                minimumLanguageVersion,
                capabilities,
                status.canGenerate() ? "1" : "unavailable",
                status.canGenerate() ? "default" : "none",
                status.canGenerate() ? 1 : 0,
                List.of());
    }

    /** Metadata for a catalogued target that has no generator in this compiler. */
    public static TargetDescriptor planned(
            String id, String displayName, String language, String framework) {
        return new TargetDescriptor(
                TargetId.of(id),
                displayName,
                language,
                framework,
                TargetStatus.NOT_SUPPORTED,
                "not applicable",
                0,
                Set.of(),
                "unavailable",
                "none",
                0,
                List.of());
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    public boolean supportsLanguageVersion(int version) {
        return status.canGenerate() && version >= minimumLanguageVersion;
    }

    /** Capabilities in declaration order of the enum, for stable output. */
    public Set<Capability> orderedCapabilities() {
        return new TreeSet<>(capabilities);
    }
}
