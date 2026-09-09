package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.TypedExpression;
import java.util.Objects;

/**
 * A condition the input of an operation must satisfy before the flow runs.
 *
 * <p>The condition arrives typed, so a target renders it rather than re-deriving what it means.
 * {@code text} is the sentence the specification wrote, which is what the failure should say.
 */
public record ApplicationRule(String text, TypedExpression condition, SourceRef where) {
    public ApplicationRule {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(where, "where");
    }
}
