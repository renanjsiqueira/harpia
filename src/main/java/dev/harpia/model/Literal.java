package dev.harpia.model;

import java.util.Objects;

/** A validated Harpia literal, retained in its canonical source representation. */
public record Literal(String source) {
    public Literal {
        Objects.requireNonNull(source, "source");
    }
}
