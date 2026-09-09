package dev.harpia.application;

import dev.harpia.capability.ResolvedCapabilities;
import java.util.List;
import java.util.Objects;

/** Generator-facing Application IR. It contains resolved providers but no Java source. */
public record ApplicationProject(
        ProjectSettings settings,
        List<ApplicationEnum> enums,
        List<ApplicationValue> values,
        List<ApplicationEntity> entities,
        List<ApplicationLogic> logics,
        List<ApplicationScenario> scenarios,
        ResolvedCapabilities capabilities) {
    public ApplicationProject {
        Objects.requireNonNull(settings, "settings");
        enums = List.copyOf(enums);
        values = List.copyOf(values);
        entities = List.copyOf(entities);
        logics = List.copyOf(logics);
        scenarios = List.copyOf(scenarios);
        Objects.requireNonNull(capabilities, "capabilities");
    }
}
