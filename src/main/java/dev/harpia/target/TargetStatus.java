package dev.harpia.target;

/**
 * Lifecycle of a target.
 *
 * <pre>
 * NOT_SUPPORTED -> EXPERIMENTAL -> SUPPORTED
 * </pre>
 *
 * <p>{@link #EXPERIMENTAL} means a generator exists but is not guaranteed for production. No target
 * is experimental today: {@code java-spring} is {@link #SUPPORTED} and every catalogued future
 * target is {@link #NOT_SUPPORTED} with no generator at all.
 */
public enum TargetStatus {
    SUPPORTED,
    EXPERIMENTAL,
    NOT_SUPPORTED;

    public boolean canGenerate() {
        return this == SUPPORTED || this == EXPERIMENTAL;
    }
}
