package dev.harpia.diag;

import java.util.Locale;

/**
 * Diagnostic severity. Only {@code ERROR} affects the exit code; warnings keep it at 0.
 */
public enum Severity {
    ERROR,
    WARNING;

    /** Lowercase label used in the compiler-style output line ({@code error}, {@code warning}). */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
