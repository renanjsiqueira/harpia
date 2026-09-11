package dev.harpia.target;

import dev.harpia.capability.Capability;
import dev.harpia.capability.CapabilityRequirement;
import dev.harpia.capability.CapabilityRequirementSet;
import dev.harpia.config.HarpiaConfig;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns configuration into a target, before any code is generated.
 *
 * <p>An unsupported target never falls back to another one, never generates a partial project and
 * never asks a model for help. It fails with a diagnostic that names what this compiler can do.
 */
public final class TargetResolver {

    private static final String CONFIG_FILE = "harpia.yaml";

    private TargetResolver() {
    }

    /**
     * @param stage {@link Stage#VALIDATION} reports an unsupported target as a warning, because the
     *     specification itself may be perfectly valid; {@link Stage#GENERATION} reports it as an
     *     error, because nothing can be produced.
     */
    public static Optional<TargetResolution> resolve(
            TargetRegistry registry,
            HarpiaConfig config,
            Stage stage,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(diagnostics, "diagnostics");

        TargetId id;
        try {
            id = TargetId.of(config.target().id());
        } catch (IllegalArgumentException exception) {
            diagnostics.error(
                    ErrorCodes.TARGET_UNKNOWN,
                    "invalid target identifier '" + config.target().id() + "'",
                    SourceRef.file(CONFIG_FILE),
                    knownTargets(registry));
            return Optional.empty();
        }

        // A registered target describes itself. The catalogue answers only for targets that have
        // no generator, so registering one does not require editing a built-in list.
        Optional<HarpiaTarget> generator = registry.find(id);
        Optional<TargetDescriptor> descriptor = generator
                .map(HarpiaTarget::descriptor)
                .or(() -> TargetCatalog.find(id));
        if (descriptor.isEmpty()) {
            diagnostics.error(
                    ErrorCodes.TARGET_UNKNOWN,
                    "unknown target `" + id + "`",
                    SourceRef.file(CONFIG_FILE),
                    knownTargets(registry));
            return Optional.empty();
        }

        TargetConfiguration configuration = new TargetConfiguration(
                id,
                config.target().languageVersion(),
                config.project().group(),
                config.project().artifact(),
                config.target().options(),
                config.target().properties());

        if (generator.isEmpty()) {
            String message = "target `" + id + "` is not supported by this compiler; target status: "
                    + descriptor.orElseThrow().status();
            if (stage == Stage.GENERATION) {
                diagnostics.error(
                        ErrorCodes.TARGET_NOT_SUPPORTED,
                        message + "; no code was generated",
                        SourceRef.file(CONFIG_FILE),
                        knownTargets(registry));
            } else {
                diagnostics.warning(
                        ErrorCodes.TARGET_NOT_SUPPORTED,
                        message + "; the specification was still validated",
                        SourceRef.file(CONFIG_FILE),
                        knownTargets(registry));
            }
            return Optional.of(new TargetResolution(
                    descriptor.orElseThrow(), Optional.empty(), configuration));
        }

        if (!descriptor.orElseThrow().supportsLanguageVersion(configuration.languageVersion())) {
            diagnostics.error(
                    ErrorCodes.TARGET_LANGUAGE_VERSION,
                    "target `" + id + "` requires " + descriptor.orElseThrow().languageRequirement()
                            + " but the configured language version is "
                            + configuration.languageVersion(),
                    SourceRef.file(CONFIG_FILE));
            return Optional.empty();
        }

        return Optional.of(new TargetResolution(
                descriptor.orElseThrow(), generator, configuration));
    }

    /** Rejects a specification whose requirements exceed what the resolved target implements. */
    public static boolean supportsEveryRequirement(
            TargetDescriptor descriptor,
            CapabilityRequirementSet requirements,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(requirements, "requirements");
        boolean supported = true;
        for (Capability capability : requirements.asMap().keySet()) {
            if (descriptor.supports(capability)) {
                continue;
            }
            CapabilityRequirement first = requirements.forCapability(capability).getFirst();
            diagnostics.error(
                    ErrorCodes.TARGET_CAPABILITY_UNSUPPORTED,
                    "target `" + descriptor.id() + "` does not currently support capability `"
                            + capability.id() + "`",
                    first.where());
            supported = false;
        }
        return supported;
    }

    /**
     * Names what this compiler can actually do. Supported means registered, because a catalogue
     * entry without a generator produces nothing; planned entries are listed separately so the
     * reader can tell an intent apart from a capability.
     */
    private static String knownTargets(TargetRegistry registry) {
        List<TargetId> planned = TargetCatalog.planned().stream()
                .filter(id -> registry.find(id).isEmpty())
                .toList();
        return "supported targets: " + registry.ids() + "; planned targets: " + planned;
    }

    /** Whether the caller intends to generate code or only to validate the specification. */
    public enum Stage {
        VALIDATION,
        GENERATION
    }
}
