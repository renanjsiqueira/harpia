package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** Generator-facing outbound port; provider details do not belong in this model. */
public record ApplicationIntegration(String name, List<Operation> operations, SourceRef where) {
    public ApplicationIntegration {
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
