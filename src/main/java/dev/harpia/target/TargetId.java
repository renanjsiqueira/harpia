package dev.harpia.target;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable identifier of a compilation target.
 *
 * <p>A target is a language and a framework together, never a language alone: {@code java} does not
 * describe enough architecture to generate an application, while {@code java-spring} does.
 *
 * <p>This is a value object rather than an enum so that a community or plugin target can exist
 * without changing the compiler core.
 */
public record TargetId(String value) implements Comparable<TargetId> {

    private static final Pattern SYNTAX = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    public TargetId {
        Objects.requireNonNull(value, "value");
        if (!SYNTAX.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid target id: " + value);
        }
    }

    public static TargetId of(String value) {
        return new TargetId(value);
    }

    @Override
    public int compareTo(TargetId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
