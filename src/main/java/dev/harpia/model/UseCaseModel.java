package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

public record UseCaseModel(
        String title,
        String baseName,
        HttpBinding http,
        AccessRule access,
        List<FieldModel> input,
        FlowModel flow,
        OutputModel output,
        List<ErrorMapping> errors,
        SourceRef where) {
    public UseCaseModel {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(baseName, "baseName");
        Objects.requireNonNull(http, "http");
        Objects.requireNonNull(access, "access");
        input = List.copyOf(input);
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(output, "output");
        errors = List.copyOf(errors);
        Objects.requireNonNull(where, "where");
    }
}
