package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/**
 * Generator-facing domain event; the transport that carries it does not belong in this model.
 *
 * <p>Whether a subscriber is in this process or across a broker is a provider's decision. What the
 * event says is the same either way, and that is all this record holds.
 */
public record ApplicationEvent(String name, List<Field> payload, SourceRef where) {
    public ApplicationEvent {
        Objects.requireNonNull(name, "name");
        payload = List.copyOf(payload);
        Objects.requireNonNull(where, "where");
    }

    public record Field(
            String name, ApplicationFieldType type, boolean required, SourceRef where) {
        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }
}
