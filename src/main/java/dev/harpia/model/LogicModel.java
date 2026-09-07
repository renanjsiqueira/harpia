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
        SourceRef where) {

    public LogicModel {
        Objects.requireNonNull(name, "name");
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(returnType, "returnType");
        body = List.copyOf(body);
        Objects.requireNonNull(where, "where");
        if (body.isEmpty()) {
            throw new IllegalArgumentException("logic " + name + " has an empty body");
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
