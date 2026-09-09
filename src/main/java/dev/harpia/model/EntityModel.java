package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

public record EntityModel(
        String name,
        List<FieldModel> fields,
        FieldModel idField,
        List<UseCaseModel> useCases,
        List<RuleModel> invariants,
        SourceRef where) {
    public EntityModel {
        Objects.requireNonNull(name, "name");
        fields = List.copyOf(fields);
        Objects.requireNonNull(idField, "idField");
        useCases = List.copyOf(useCases);
        invariants = List.copyOf(invariants);
        Objects.requireNonNull(where, "where");
    }
}
