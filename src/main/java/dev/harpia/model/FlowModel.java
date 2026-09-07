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
