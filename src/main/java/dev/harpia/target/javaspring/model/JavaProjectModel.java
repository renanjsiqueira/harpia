package dev.harpia.target.javaspring.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete Java source model produced by one Java/Spring transformation. */
public record JavaProjectModel(List<JavaSourceFile> sourceFiles) {

    public JavaProjectModel {
        Objects.requireNonNull(sourceFiles, "sourceFiles");
        sourceFiles = sourceFiles.stream().sorted().toList();
        Set<String> paths = new HashSet<>();
        for (JavaSourceFile file : sourceFiles) {
            if (!paths.add(file.relativePath())) {
                throw new IllegalArgumentException("duplicate Java source path: " + file.relativePath());
            }
        }
    }
}
