package dev.harpia.logic;

/** The closed set of binary operators accepted inside a Harpia Logic expression. */
public enum BinaryOperator {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    EQUAL("=="),
    NOT_EQUAL("!="),
    LESS("<"),
    LESS_OR_EQUAL("<="),
    GREATER(">"),
    GREATER_OR_EQUAL(">="),
    AND("and"),
    OR("or");

    private final String symbol;

    BinaryOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean isArithmetic() {
        return this == ADD || this == SUBTRACT || this == MULTIPLY || this == DIVIDE;
    }

    public boolean isOrdering() {
        return this == LESS || this == LESS_OR_EQUAL || this == GREATER || this == GREATER_OR_EQUAL;
    }

    public boolean isEquality() {
        return this == EQUAL || this == NOT_EQUAL;
    }

    public boolean isLogical() {
        return this == AND || this == OR;
    }
}
