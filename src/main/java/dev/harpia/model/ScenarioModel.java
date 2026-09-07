package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import java.util.List;
import java.util.Objects;

/**
 * Business IR of one declared example: the values a computation is given and the result it must
 * produce.
 *
 * <p>A pure computation has no observable behaviour a compiler can derive an expectation from. The
 * expected value is knowledge that only a person has, and this is where it enters the pipeline.
 */
public record ScenarioModel(
        String title,
        String methodName,
        String computation,
        List<Binding> arguments,
        LogicType resultType,
        Literal expected,
        SourceRef where) {

    public ScenarioModel {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(computation, "computation");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(where, "where");
    }

    /** One argument, already ordered by the computation's declared inputs. */
    public record Binding(String name, LogicType type, Literal value, SourceRef where) {
        public Binding {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }
    }
}
