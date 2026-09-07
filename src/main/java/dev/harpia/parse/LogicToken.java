package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
import java.util.Objects;

/** One lexical token of a {@code logic} block, carrying its exact source position. */
public record LogicToken(LogicToken.Kind kind, String text, SourceRef where) {

    public LogicToken {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(where, "where");
    }

    public boolean is(Kind expected) {
        return kind == expected;
    }

    public enum Kind {
        IDENTIFIER,
        TYPE_NAME,
        INTEGER,
        DECIMAL,
        STRING,
        IF,
        ELSE,
        RETURN,
        AND,
        OR,
        NOT,
        TRUE,
        FALSE,
        /** A word reserved for a planned Harpia Logic feature. */
        RESERVED,
        /** A word that names a side-effecting operation and therefore belongs to Flow. */
        EFFECT,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        EQUAL_EQUAL,
        NOT_EQUAL,
        LESS,
        LESS_OR_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        ASSIGN,
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA,
        DOT,
        END_OF_LINE
    }
}
