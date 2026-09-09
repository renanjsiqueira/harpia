package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** A closed set of values the project declared, in declaration order. */
public record EnumModel(String name, List<String> values, SourceRef where) {
    public EnumModel {
        Objects.requireNonNull(name, "name");
        values = List.copyOf(values);
        Objects.requireNonNull(where, "where");
    }
}
