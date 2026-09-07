package dev.harpia.logic;

/**
 * Effect classification used to enforce the purity boundary between computation and orchestration.
 *
 * <p>This model is internal. It has no surface syntax and never appears in {@code harpia.yaml}.
 * Formula, Decision and Logic accept only {@link #PURE}; Flow accepts the remaining effects
 * according to the capability resolved for each operation.
 */
public enum Effect {
    PURE,
    PERSISTENCE_READ,
    PERSISTENCE_WRITE,
    INTEGRATION,
    EVENT,
    EMAIL,
    STORAGE
}
