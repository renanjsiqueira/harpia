package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** Framework-free outbound port owned by the business specification. */
public record IntegrationModel(String name, List<Operation> operations, SourceRef where) {
    public IntegrationModel {
        Objects.requireNonNull(name, "name");
        operations = List.copyOf(operations);
        Objects.requireNonNull(where, "where");
    }

    public record Operation(
            String name,
            List<Parameter> input,
            Result output,
            List<Failure> errors,
            SourceRef where) {
        public Operation {
            Objects.requireNonNull(name, "name");
            input = List.copyOf(input);
            Objects.requireNonNull(output, "output");
            errors = List.copyOf(errors);
            Objects.requireNonNull(where, "where");
        }
    }

    public record Parameter(String name, FieldType type, boolean required, SourceRef where) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    /** An empty type represents the explicit {@code nothing} result. */
    public record Result(java.util.Optional<FieldType> type, SourceRef where) {
        public Result {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Failure(String name, SourceRef where) {
        public Failure {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }
}
