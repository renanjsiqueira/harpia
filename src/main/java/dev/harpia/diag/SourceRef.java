package dev.harpia.diag;

import java.util.Objects;

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
 */
public record SourceRef(String file, int line, int column) {

    public SourceRef {
        Objects.requireNonNull(file, "file");
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
    }

    /** Reference to a position inside a file. Both coordinates are 1-indexed. */
    public static SourceRef of(String file, int line, int column) {
        return new SourceRef(file, line, column);
    }

    /** Reference to a whole file, without a position. */
    public static SourceRef file(String file) {
        return new SourceRef(file, 0, 0);
    }

    public boolean hasPosition() {
        return line > 0;
    }
}
