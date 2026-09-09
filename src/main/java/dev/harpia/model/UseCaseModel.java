package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record UseCaseModel(
        OperationNature nature,
        String title,
        String baseName,
        Optional<HttpBinding> http,
        List<FieldModel> input,
        List<RuleModel> rules,
        FlowModel flow,
        OutputModel output,
        List<ErrorMapping> errors,
        SourceRef where) {
    public UseCaseModel {
        Objects.requireNonNull(nature, "nature");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(baseName, "baseName");
        Objects.requireNonNull(http, "http");
        input = List.copyOf(input);
        rules = List.copyOf(rules);
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(output, "output");
        errors = List.copyOf(errors);
        Objects.requireNonNull(where, "where");
    }
}
