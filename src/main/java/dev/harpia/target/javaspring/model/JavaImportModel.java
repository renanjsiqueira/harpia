package dev.harpia.target.javaspring.model;

import java.util.Objects;

/** An explicit import required by target-specific source expressions. */
public record JavaImportModel(String canonicalName) implements Comparable<JavaImportModel> {

    public JavaImportModel {
        Objects.requireNonNull(canonicalName, "canonicalName");
        if (canonicalName.isBlank() || !canonicalName.contains(".")) {
            throw new IllegalArgumentException("an import must be a qualified Java name");
        }
    }

    @Override
    public int compareTo(JavaImportModel other) {
        return canonicalName.compareTo(other.canonicalName);
    }
}
