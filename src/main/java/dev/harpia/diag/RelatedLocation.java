package dev.harpia.diag;

import java.util.Objects;

/**
 * A second place a diagnostic points at.
 *
 * <p>A duplicate name means two declarations, and only one of them can be the main location. The
 * other used to be pasted into the message as {@code first declared at file:line:column}, which
 * made the message the only place that knew it: a formatter could not align it, a JSON output could
 * not expose it, and an editor could not turn it into a second underline. It is data now.
 */
public record RelatedLocation(String message, SourceRef where)
        implements Comparable<RelatedLocation> {

    public RelatedLocation {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(where, "where");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    @Override
    public int compareTo(RelatedLocation other) {
        int byFile = where.file().compareTo(other.where.file());
        if (byFile != 0) {
            return byFile;
        }
        int byLine = Integer.compare(where.line(), other.where.line());
        if (byLine != 0) {
            return byLine;
        }
        int byColumn = Integer.compare(where.column(), other.where.column());
        return byColumn != 0 ? byColumn : message.compareTo(other.message);
    }
}
