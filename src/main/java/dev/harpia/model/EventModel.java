package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/**
 * Framework-free domain event owned by the business specification.
 *
 * <p>An event is not a call: nothing waits for it and nothing answers, so it has no result and no
 * failures. What it has is the payload, which is the whole of what it says.
 */
public record EventModel(String name, List<Field> payload, SourceRef where) {
    public EventModel {
        Objects.requireNonNull(name, "name");
        payload = List.copyOf(payload);
        Objects.requireNonNull(where, "where");
    }

    public record Field(String name, FieldType type, boolean required, SourceRef where) {
        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }
}
