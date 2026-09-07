package dev.harpia.target.javaspring.model;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

/** One Java compilation unit and its closest Harpia source location. */
public record JavaSourceFile(
        String relativePath,
        JavaTypeModel type,
        Optional<SourceRef> source) implements Comparable<JavaSourceFile> {

    public JavaSourceFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
    }

    @Override
    public int compareTo(JavaSourceFile other) {
        return relativePath.compareTo(other.relativePath);
    }
}
