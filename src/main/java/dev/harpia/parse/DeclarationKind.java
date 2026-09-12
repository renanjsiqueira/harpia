package dev.harpia.parse;

/** Stable syntax-level kind used to route and inspect declarations. */
public enum DeclarationKind {
    ENTITY,
    ENUM,
    VALUE,
    INVARIANT,
    USE_CASE,
    COMMAND,
    QUERY,
    INTEGRATION,
    EVENT,
    LOGIC,
    SCENARIO;

    /** True when this declaration is an application operation, whichever syntax declared it. */
    public boolean isOperation() {
        return this == USE_CASE || this == COMMAND || this == QUERY;
    }
}
