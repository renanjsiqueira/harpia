package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

public record ErrorMapping(
        Condition condition, Optional<String> field, int status, SourceRef where) {
    public ErrorMapping {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(where, "where");
    }

    public enum Condition {
        INVALID_INPUT,
        DUPLICATE,
        NOT_FOUND
    }
}
