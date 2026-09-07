package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedStatement;
import java.util.List;
import java.util.Objects;

/**
 * Application-level pure computation: a named function with typed parameters and a typed result.
 *
 * <p>The body is the target-independent statement algebra. How it becomes a class, a function or a
 * static method, and which types it uses, is decided by the target.
 */
public record ApplicationLogic(
        String typeName,
        List<Parameter> parameters,
        LogicType returnType,
        List<TypedStatement> body,
        SourceRef where) {

    public ApplicationLogic {
        Objects.requireNonNull(typeName, "typeName");
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(returnType, "returnType");
        body = List.copyOf(body);
        Objects.requireNonNull(where, "where");
    }

    public record Parameter(String name, LogicType type) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }
}
