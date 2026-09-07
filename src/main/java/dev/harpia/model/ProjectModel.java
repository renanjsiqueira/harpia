package dev.harpia.model;

import java.util.List;

/**
 * Framework-agnostic Business IR for the whole validated project.
 *
 * <p>{@code logics} are pure computations. They belong to the project, not to an entity, and they
 * carry no capability requirement because they have no effect.
 */
public record ProjectModel(
        List<EntityModel> entities,
        List<LogicModel> logics,
        List<ScenarioModel> scenarios) {
    public ProjectModel {
        entities = List.copyOf(entities);
        logics = List.copyOf(logics);
        scenarios = List.copyOf(scenarios);
    }
}
