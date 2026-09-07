package dev.harpia.logic;

/** The closed set of prefix operators accepted inside a Harpia Logic expression. */
public enum UnaryOperator {
    NEGATE("-"),
    NOT("not");

    private final String symbol;

    UnaryOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
