package dev.harpia;

import java.util.Arrays;
import java.util.Optional;

/** Version of the Harpia language grammar and semantics understood by the compiler. */
public enum LanguageVersion {
    /** Single entity per module, CRUD use cases with a mandatory endpoint, and pure Logic. */
    V0(0),
    /** Adds {@code ## Command} and {@code ## Query}: an operation declares what it is. */
    V1(1);

    /**
     * Latest language version implemented by this compiler.
     *
     * <p>A project that does not say which version it speaks gets this one. A project that says
     * {@code languageVersion: 0} keeps the V0 grammar, so nothing already written changes meaning.
     */
    public static final LanguageVersion CURRENT = V1;

    private final int number;

    LanguageVersion(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    public static Optional<LanguageVersion> from(int number) {
        return Arrays.stream(values())
                .filter(version -> version.number == number)
                .findFirst();
    }

    public static String supportedNumbers() {
        return Arrays.stream(values())
                .map(version -> Integer.toString(version.number))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return Integer.toString(number);
    }
}
