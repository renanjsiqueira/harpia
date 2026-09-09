package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedStatement;
import java.util.List;
import java.util.Objects;

/**
 * Business IR of one pure computation. It is framework free: there is no Java type, no annotation
 * and no capability requirement, because Logic has no effect.
 */
public record LogicModel(
        String name,
        List<Parameter> parameters,
        LogicType returnType,
        List<TypedStatement> body,
        java.util.Optional<String> customContract,
        SourceRef where) {

    /** A computation Harpia understands and generates in full. */
    public LogicModel(
            String name,
            List<Parameter> parameters,
            LogicType returnType,
            List<TypedStatement> body,
            SourceRef where) {
        this(name, parameters, returnType, body, java.util.Optional.empty(), where);
    }

    public LogicModel {
        Objects.requireNonNull(name, "name");
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(returnType, "returnType");
        body = List.copyOf(body);
        Objects.requireNonNull(customContract, "customContract");
        Objects.requireNonNull(where, "where");
        if (body.isEmpty() == customContract.isEmpty()) {
            throw new IllegalArgumentException(
                    "logic " + name + " needs exactly one of a body or a custom contract");
        }
    }

    public record Parameter(String name, LogicType type, SourceRef where) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }
}
