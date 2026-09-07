package dev.harpia.target;

import java.util.List;
import java.util.Objects;

/** Stable target metadata suitable for CLI, MCP or another discovery adapter. */
public record TargetInfo(
        String id,
        String name,
        TargetStatus status,
        String language,
        String framework,
        String languageRequirement,
        String targetVersion,
        String templateSet,
        int templateVersion,
        List<TargetCapabilityInfo> capabilities) {

    public TargetInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(framework, "framework");
        Objects.requireNonNull(languageRequirement, "languageRequirement");
        Objects.requireNonNull(targetVersion, "targetVersion");
        Objects.requireNonNull(templateSet, "templateSet");
        capabilities = capabilities.stream().sorted().toList();
    }

    public static TargetInfo from(TargetDescriptor descriptor) {
        return new TargetInfo(
                descriptor.id().value(),
                descriptor.displayName(),
                descriptor.status(),
                descriptor.language(),
                descriptor.framework(),
                descriptor.languageRequirement(),
                descriptor.targetVersion(),
                descriptor.templateSet(),
                descriptor.templateVersion(),
                descriptor.orderedCapabilities().stream()
                        .map(capability -> new TargetCapabilityInfo(capability.id()))
                        .toList());
    }
}
