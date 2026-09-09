package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** A named group of fields a target must materialise before any field can hold one. */
public record ApplicationValue(
        String typeName, List<ApplicationField> components, SourceRef where) {
    public ApplicationValue {
        Objects.requireNonNull(typeName, "typeName");
        components = List.copyOf(components);
        Objects.requireNonNull(where, "where");
    }
}
