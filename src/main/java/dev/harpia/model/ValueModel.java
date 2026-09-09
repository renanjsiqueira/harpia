package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** A named group of fields, compared by what it holds rather than by an identity. */
public record ValueModel(String name, List<FieldModel> components, SourceRef where) {
    public ValueModel {
        Objects.requireNonNull(name, "name");
        components = List.copyOf(components);
        Objects.requireNonNull(where, "where");
    }
}
