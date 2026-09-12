package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedExpression;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A Flow invocation resolved to one operation of a typed outbound port. */
public record IntegrationCallModel(
        Optional<String> variable,
        String integration,
        String operation,
        List<Argument> arguments,
        Optional<LogicType> resultType,
        SourceRef where) {
    public IntegrationCallModel {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(integration, "integration");
        Objects.requireNonNull(operation, "operation");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(where, "where");
    }

    public String target() {
        return integration + "." + operation;
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
