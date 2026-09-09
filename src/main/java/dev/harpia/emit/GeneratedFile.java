package dev.harpia.emit;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One in-memory generated artifact, optionally traceable to the Harpia source that caused it. */
public record GeneratedFile(
        String relativePath,
        String content,
        GeneratedFileType type,
        Optional<SourceRef> source,
        List<GeneratedSourceMapping> sourceMappings) implements Comparable<GeneratedFile> {

    public GeneratedFile(
            String relativePath,
            String content,
            GeneratedFileType type,
            Optional<SourceRef> source) {
        this(relativePath, content, type, source, List.of());
    }

    public GeneratedFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceMappings, "sourceMappings");
        sourceMappings = sourceMappings.stream().sorted().toList();
        GeneratedTree.validatePath(relativePath);
        if (sourceMappings.stream()
                .anyMatch(mapping -> !mapping.generated().file().equals(relativePath))) {
            throw new IllegalArgumentException(
                    "generated mappings must target their owning file: " + relativePath);
        }
    }

    public static GeneratedFile other(String relativePath, String content) {
        return new GeneratedFile(relativePath, content, GeneratedFileType.OTHER, Optional.empty());
    }

    @Override
    public int compareTo(GeneratedFile other) {
        return relativePath.compareTo(other.relativePath);
    }
}
