package dev.harpia.emit;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

/** One in-memory generated artifact, optionally traceable to the Harpia source that caused it. */
public record GeneratedFile(
        String relativePath,
        String content,
        GeneratedFileType type,
        Optional<SourceRef> source) implements Comparable<GeneratedFile> {

    public GeneratedFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        GeneratedTree.validatePath(relativePath);
    }

    public static GeneratedFile other(String relativePath, String content) {
        return new GeneratedFile(relativePath, content, GeneratedFileType.OTHER, Optional.empty());
    }

    @Override
    public int compareTo(GeneratedFile other) {
        return relativePath.compareTo(other.relativePath);
    }
}
