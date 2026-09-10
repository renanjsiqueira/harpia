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

    public record Operation(String name, SourceRef where) {
        public Operation {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }
}
