package dev.harpia.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FlowModel(List<FlowStep> steps, Map<String, ValueType> variables) {
    public FlowModel {
        steps = List.copyOf(steps);
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    /** Every step in source order, including steps nested in conditional branches. */
    public List<FlowStep> allSteps() {
        java.util.ArrayList<FlowStep> result = new java.util.ArrayList<>();
        append(steps, result);
        return List.copyOf(result);
    }

    private static void append(List<FlowStep> source, List<FlowStep> target) {
        for (FlowStep step : source) {
            target.add(step);
            if (step instanceof FlowStep.Conditional conditional) {
                append(conditional.whenTrue(), target);
                append(conditional.whenFalse(), target);
            }
        }
    }

    public record ValueType(Kind kind, String entity) {
        public ValueType {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(entity, "entity");
        }
    }

    public enum Kind {
        ENTITY,
        LIST
    }
}
