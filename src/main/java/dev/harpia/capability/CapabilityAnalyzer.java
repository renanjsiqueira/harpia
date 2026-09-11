package dev.harpia.capability;

import dev.harpia.model.EntityModel;
import dev.harpia.model.FlowStep;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.UseCaseModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Infers abstract technical requirements from the framework-free Business IR. */
public final class CapabilityAnalyzer {

    private CapabilityAnalyzer() {
    }

    public static CapabilityRequirementSet analyze(ProjectModel project) {
        Objects.requireNonNull(project, "project");
        List<CapabilityRequirement> requirements = new ArrayList<>();
        for (EntityModel entity : project.entities()) {
            requirements.add(new CapabilityRequirement(
                    Capability.PERSISTENCE,
                    "entity " + entity.name() + " declares persistent data",
                    entity.where()));
            for (UseCaseModel useCase : entity.useCases()) {
                useCase.http().ifPresent(ignored -> requirements.add(new CapabilityRequirement(
                        Capability.HTTP,
                        "operation " + useCase.baseName() + " declares an HTTP binding",
                        useCase.where())));
                for (FlowStep step : useCase.flow().allSteps()) {
                    if (step instanceof FlowStep.IntegrationCall call) {
                        requirements.add(new CapabilityRequirement(
                                Capability.HTTP,
                                "integration call " + call.integration() + "." + call.operation()
                                        + " requires an outbound provider",
                                step.where()));
                    }
                    if (usesPersistence(step)) {
                        requirements.add(new CapabilityRequirement(
                                Capability.PERSISTENCE,
                                "flow step " + step.getClass().getSimpleName(),
                                step.where()));
                    }
                }
            }
        }
        project.integrations().forEach(integration -> integration.operations().forEach(operation ->
                operation.http().ifPresent(binding -> requirements.add(new CapabilityRequirement(
                        Capability.HTTP,
                        "Integration operation " + integration.name() + "." + operation.name()
                                + " declares an outbound HTTP binding",
                        binding.where())))));
        return new CapabilityRequirementSet(requirements);
    }

    private static boolean usesPersistence(FlowStep step) {
        return step instanceof FlowStep.LoadById
                || step instanceof FlowStep.ListAll
                || step instanceof FlowStep.Save
                || step instanceof FlowStep.Delete;
    }
}
