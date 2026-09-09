package dev.harpia.model;

/**
 * What an operation is, as far as the specification says.
 *
 * <p>{@link #COMMAND} and {@link #QUERY} are declared: the author stated the intent, and the
 * compiler holds them to it. {@link #INFERRED} is the V0 heading, where nothing was declared and
 * the nature has to be guessed from the shape of the flow — which is exactly what declaring it
 * replaces.
 */
public enum OperationNature {
    COMMAND,
    QUERY,
    INFERRED;

    public boolean isDeclared() {
        return this != INFERRED;
    }
}
