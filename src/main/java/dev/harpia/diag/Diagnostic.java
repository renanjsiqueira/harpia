package dev.harpia.diag;

import java.util.List;
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
                         List<RelatedLocation> related,
                         Optional<String> hint) {

    public Diagnostic(
            Severity severity,
            String code,
            String message,
            Optional<SourceRef> where,
            Optional<String> hint) {
        this(severity, code, message, where, List.of(), hint);
    }

    public Diagnostic {
        related = List.copyOf(related);
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
        return new Diagnostic(severity, code, message, where, related, Optional.of(newHint));
    }

    /** The same diagnostic, also pointing at another place that explains it. */
    public Diagnostic relatedTo(String relatedMessage, SourceRef relatedWhere) {
        List<RelatedLocation> extended = new java.util.ArrayList<>(related);
        extended.add(new RelatedLocation(relatedMessage, relatedWhere));
        return new Diagnostic(severity, code, message, where, extended, hint);
    }
}
