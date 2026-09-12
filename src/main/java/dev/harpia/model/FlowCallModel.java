package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedExpression;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A Flow invocation resolved to a pure Logic signature. */
public record FlowCallModel(
        Optional<String> variable,
        String logic,
        List<Argument> arguments,
        LogicType resultType,
        Optional<String> customContract,
        SourceRef where) {
    public FlowCallModel {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(logic, "logic");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(customContract, "customContract");
        Objects.requireNonNull(where, "where");
    }

    public record Argument(
            String name,
            TypedExpression value,
            LogicType parameterType,
            SourceRef where) {
        public Argument {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(parameterType, "parameterType");
            Objects.requireNonNull(where, "where");
        }
    }
}
