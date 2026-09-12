package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedExpression;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A Flow invocation resolved to a declared application Command. */
public record OperationCallModel(
        Optional<String> variable,
        String operation,
        String entity,
        boolean requiresId,
        List<Argument> arguments,
        OutputModel.Kind resultKind,
        SourceRef where) {
    public OperationCallModel {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(entity, "entity");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(resultKind, "resultKind");
        Objects.requireNonNull(where, "where");
        if (variable.isPresent() == (resultKind == OutputModel.Kind.NOTHING)) {
            throw new IllegalArgumentException(
                    "a Command result is assigned exactly when it returns a value");
        }
    }

    public record Argument(
            String name,
            TypedExpression value,
            LogicType parameterType,
            boolean identifier,
            SourceRef where) {
        public Argument {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(parameterType, "parameterType");
            Objects.requireNonNull(where, "where");
        }
    }
}
