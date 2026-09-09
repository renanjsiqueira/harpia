package dev.harpia.diag;

import java.util.Objects;
import java.util.Optional;

/**
 * Location of a diagnostic inside a source file.
 *
 * <p>{@code file} is a project-relative path that always uses {@code '/'} as separator. It is a
 * {@code String} and not a {@link java.nio.file.Path} on purpose: a {@code Path} carries the OS
 * separator and would leak {@code '\'} into messages and golden files on Windows, breaking
 * cross-platform determinism.
 *
 * <p>{@code line} and {@code column} are 1-indexed. Both are {@code 0} when the diagnostic refers
 * to a file as a whole (for example, a missing {@code harpia.yaml}).
 *
 * <p>{@code end} is the exclusive end of the referenced text when it is known. It is optional
 * because most positions are produced from a single point and inventing an end would be a lie; a
 * renderer that wants to underline a span asks for it and falls back to the start when it is
 * absent. This is the role the V1 draft calls {@code SourceRange}; it lives on the same type so the
 * whole compiler did not have to be rewritten to gain an end coordinate.
 */
public record SourceRef(String file, int line, int column, Optional<Position> end) {

    public SourceRef(String file, int line, int column) {
        this(file, line, column, Optional.empty());
    }

    public SourceRef {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(end, "end");
        if (file.isBlank()) {
            throw new IllegalArgumentException("file must not be blank");
        }
        if (file.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("file must use '/' as separator: " + file);
        }
        if (line < 0 || column < 0) {
            throw new IllegalArgumentException("line/column must not be negative: " + line + ":" + column);
        }
        if ((line == 0) != (column == 0)) {
            throw new IllegalArgumentException(
                    "line and column must both be set or both be 0: " + line + ":" + column);
        }
        if (end.isPresent() && line == 0) {
            throw new IllegalArgumentException("a whole-file reference has no end");
        }
        if (end.filter(position -> position.line() < line
                || position.line() == line && position.column() < column).isPresent()) {
            throw new IllegalArgumentException("an end must not precede its start");
        }
    }

    /** Reference to a position inside a file. Both coordinates are 1-indexed. */
    public static SourceRef of(String file, int line, int column) {
        return new SourceRef(file, line, column);
    }

    /** Reference to a whole file, without a position. */
    public static SourceRef file(String file) {
        return new SourceRef(file, 0, 0);
    }

    /** Reference to a span of text. The end is exclusive and both coordinates are 1-indexed. */
    public static SourceRef span(
            String file, int line, int column, int endLine, int endColumn) {
        return new SourceRef(
                file, line, column, Optional.of(new Position(endLine, endColumn)));
    }

    /** The same start, now knowing where the referenced text ends. */
    public SourceRef endingAt(int endLine, int endColumn) {
        return new SourceRef(file, line, column, Optional.of(new Position(endLine, endColumn)));
    }

    /** Drops the end, for a caller that only cares where something begins. */
    public SourceRef start() {
        return end.isEmpty() ? this : new SourceRef(file, line, column);
    }

    public boolean hasPosition() {
        return line > 0;
    }

    /** {@code file:line:column} for a point, {@code file:line:column-line:column} for a span. */
    public String describe() {
        if (!hasPosition()) {
            return file;
        }
        return file + ":" + line + ":" + column
                + end.map(position -> "-" + position.line() + ":" + position.column()).orElse("");
    }

    /** The exclusive end of a referenced span. */
    public record Position(int line, int column) {
        public Position {
            if (line < 1 || column < 1) {
                throw new IllegalArgumentException(
                        "an end position is 1-indexed: " + line + ":" + column);
            }
        }
    }
}
