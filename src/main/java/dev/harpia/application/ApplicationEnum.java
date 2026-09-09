package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** A closed set of values a target must materialise before any field can hold one. */
public record ApplicationEnum(String typeName, List<String> values, SourceRef where) {
    public ApplicationEnum {
        Objects.requireNonNull(typeName, "typeName");
        values = List.copyOf(values);
        Objects.requireNonNull(where, "where");
    }
}
