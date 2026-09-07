package dev.harpia.application;

/**
 * Scalar vocabulary of the Application IR.
 *
 * <p>It deliberately carries no target type name. Which arbitrary-precision decimal, date or
 * identifier type implements {@code DECIMAL}, {@code DATE} or {@code UUID} is each target's
 * decision, and the Harpia type system is unchanged by that choice.
 */
public enum ApplicationScalarType {
    STRING,
    TEXT,
    INT,
    LONG,
    DECIMAL,
    BOOLEAN,
    UUID,
    EMAIL,
    DATE,
    DATE_TIME
}
