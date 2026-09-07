package dev.harpia.model;

import java.util.Arrays;

/** Harpia types, deliberately independent from Java and database type names. */
public enum TypeRef {
    STRING("String"),
    TEXT("Text"),
    INT("Int"),
    LONG("Long"),
    DECIMAL("Decimal"),
    BOOLEAN("Boolean"),
    UUID("UUID"),
    EMAIL("Email"),
    DATE("Date"),
    DATE_TIME("DateTime");

    private final String syntax;

    TypeRef(String syntax) {
        this.syntax = syntax;
    }

    public String syntax() {
        return syntax;
    }

    public static TypeRef fromSyntax(String syntax) {
        return Arrays.stream(values())
                .filter(type -> type.syntax.equals(syntax))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown Harpia type: " + syntax));
    }
}
