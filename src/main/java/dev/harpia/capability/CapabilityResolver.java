package dev.harpia.capability;

import dev.harpia.config.HarpiaConfig;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Chooses a logical provider for each required capability.
 *
 * <p>This is target independent on purpose. PostgreSQL is a database whichever language generates
 * the code that talks to it, so the choice is made once here and every target honours it. What that
 * choice costs in dependencies or configuration is decided behind the target boundary.
 */
public final class CapabilityResolver {

    public static final ProviderId POSTGRESQL = new ProviderId("postgresql");
    public static final ProviderId BASIC = new ProviderId("basic");
    public static final ProviderId JWT = new ProviderId("jwt");

    private CapabilityResolver() {
    }

    public static Optional<ResolvedCapabilities> resolve(
            CapabilityRequirementSet requirements,
            HarpiaConfig config,
            DiagnosticCollector diagnostics) {
        return resolve(requirements, config, Optional.empty(), diagnostics);
    }

    /**
     * Resolves providers and refuses one that cannot satisfy what was declared.
     *
     * <p>The project is optional because a caller may only have requirements to resolve. When it
     * is present, a declaration the chosen provider has no way to honour is refused here rather
     * than generated into code that would answer every request the same wrong way.
     */
    public static Optional<ResolvedCapabilities> resolve(
            CapabilityRequirementSet requirements,
            HarpiaConfig config,
            Optional<dev.harpia.model.ProjectModel> project,
            DiagnosticCollector diagnostics) {
        project.ifPresent(model -> refuseScopeWithoutToken(model, config, diagnostics));
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<ResolvedCapability> resolved = new ArrayList<>();
        boolean valid = true;
        for (Capability capability : requirements.asMap().keySet()) {
            Selection selection = select(capability, config);
            if (!selection.satisfied()) {
                CapabilityRequirement first = requirements.forCapability(capability).getFirst();
                diagnostics.error(selection.code(), selection.message(), first.where());
                valid = false;
                continue;
            }
            resolved.add(new ResolvedCapability(
                    capability, selection.provider(), requirements.forCapability(capability)));
        }
        return valid ? Optional.of(new ResolvedCapabilities(resolved)) : Optional.empty();
    }

    /**
     * A scope comes from a token.
     *
     * <p>With basic authentication there are no scopes to carry, so the rule would match nothing
     * and every request to that endpoint would be refused — a specification that reads as an
     * access rule and behaves as a closed door.
     */
    private static void refuseScopeWithoutToken(
            dev.harpia.model.ProjectModel project,
            HarpiaConfig config,
            DiagnosticCollector diagnostics) {
        if (!config.security().provider().equals(HarpiaConfig.SecurityConfig.BASIC)) {
            return;
        }
        project.entities().stream()
                .flatMap(entity -> entity.useCases().stream())
                .flatMap(useCase -> useCase.http().stream())
                .filter(binding -> binding.access().kind() == dev.harpia.model.AccessRule.Kind.SCOPE)
                .forEach(binding -> diagnostics.error(
                        ErrorCodes.CAPABILITY_PROVIDER_UNSUPPORTED,
                        "access '" + binding.access().toString().toLowerCase(java.util.Locale.ROOT)
                                + "' needs a token: security.provider is '"
                                + config.security().provider() + "', which carries no scopes",
                        binding.where()));
    }

    private static Selection select(Capability capability, HarpiaConfig config) {
        return switch (capability) {
            // The target exposes endpoints itself; there is no separate HTTP provider to choose.
            case HTTP -> Selection.targetNative();
            case PERSISTENCE -> config.database().vendor().equals("postgres")
                    ? Selection.provider(POSTGRESQL)
                    : Selection.unsupported(
                            ErrorCodes.CAPABILITY_PROVIDER_UNSUPPORTED,
                            "capability 'persistence' requires provider 'postgresql'; configured "
                                    + "database is '" + config.database().vendor() + "'");
            // Which mechanism proves an identity is a logical choice that outlives any target,
            // the way a database vendor is: the specification says which endpoints need one, the
            // deployment says how it arrives.
            case SECURITY -> Selection.provider(
                    config.security().provider().equals(HarpiaConfig.SecurityConfig.JWT)
                            ? JWT
                            : BASIC);
            case EVENTS -> Selection.unsupported(
                    ErrorCodes.CAPABILITY_PROVIDER_MISSING,
                    "capability 'events' has no provider configured");
            case CUSTOM -> Selection.unsupported(
                    ErrorCodes.CAPABILITY_PROVIDER_MISSING,
                    "capability 'custom' has no provider configured");
        };
    }

    private record Selection(
            boolean satisfied, Optional<ProviderId> provider, String code, String message) {

        private static Selection targetNative() {
            return new Selection(true, Optional.empty(), "", "");
        }

        private static Selection provider(ProviderId provider) {
            return new Selection(true, Optional.of(provider), "", "");
        }

        private static Selection unsupported(String code, String message) {
            return new Selection(false, Optional.empty(), code, message);
        }
    }
}
