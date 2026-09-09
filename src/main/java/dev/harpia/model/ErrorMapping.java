package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

public record ErrorMapping(
        Condition condition,
        Optional<String> field,
        Optional<String> name,
        int status,
        SourceRef where) {
    public ErrorMapping {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(where, "where");
    }

    /** {@code DOMAIN} carries the name the business gave the error; the rest are detected. */
    public enum Condition {
        INVALID_INPUT,
        DUPLICATE,
        NOT_FOUND,
        DOMAIN
    }
}
