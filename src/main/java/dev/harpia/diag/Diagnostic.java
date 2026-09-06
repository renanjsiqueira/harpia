package dev.harpia.diag;

import java.util.Objects;
import java.util.Optional;

/**
 * A single compiler message. Every user-facing failure is a {@code Diagnostic}; exceptions are
 * reserved for programming bugs.
 */
public record Diagnostic(Severity severity,
                         String code,
                         String message,
                         Optional<SourceRef> where,
                         Optional<String> hint) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(hint, "hint");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message, Optional.empty(), Optional.empty());
    }

    public static Diagnostic error(String code, String message, SourceRef where) {
        return new Diagnostic(Severity.ERROR, code, message, Optional.of(where), Optional.empty());
    }

    public static Diagnostic error(String code, String message, SourceRef where, String hint) {
        return new Diagnostic(Severity.ERROR, code, message, Optional.of(where), Optional.of(hint));
    }

    public static Diagnostic warning(String code, String message) {
        return new Diagnostic(Severity.WARNING, code, message, Optional.empty(), Optional.empty());
    }

    public static Diagnostic warning(String code, String message, SourceRef where) {
        return new Diagnostic(Severity.WARNING, code, message, Optional.of(where), Optional.empty());
    }

    public static Diagnostic warning(String code, String message, SourceRef where, String hint) {
        return new Diagnostic(Severity.WARNING, code, message, Optional.of(where), Optional.of(hint));
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public Diagnostic withHint(String newHint) {
        return new Diagnostic(severity, code, message, where, Optional.of(newHint));
    }
}
