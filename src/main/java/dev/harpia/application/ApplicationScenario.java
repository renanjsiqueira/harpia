package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import java.util.List;
import java.util.Objects;

/** Application-level example: a computation, its arguments and the result it must produce. */
public record ApplicationScenario(
        String title,
        String methodName,
        String computation,
        List<Argument> arguments,
        LogicType resultType,
        String expected,
        SourceRef where) {

    public ApplicationScenario {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(computation, "computation");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(where, "where");
    }

    public record Argument(String name, LogicType type, String value) {
        public Argument {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
        }
    }
}
