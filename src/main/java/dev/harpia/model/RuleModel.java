package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.TypedExpression;
import java.util.Objects;

/**
 * A condition the input of an operation must satisfy.
 *
 * <p>{@code text} is the sentence the specification wrote. A violated rule reports itself, and what
 * a reader recognises is the rule as written, not a reconstruction of it.
 */
public record RuleModel(String text, TypedExpression condition, SourceRef where) {
    public RuleModel {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(where, "where");
    }
}
