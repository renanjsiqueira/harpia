package dev.harpia.target;

import dev.harpia.emit.GeneratedFile;
import dev.harpia.emit.GeneratedTree;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable files returned by a target before the core writer touches the filesystem. */
public record TargetGenerationResult(List<GeneratedFile> files) {

    public TargetGenerationResult {
        Objects.requireNonNull(files, "files");
        files = files.stream().sorted().toList();
        Set<String> paths = new HashSet<>();
        for (GeneratedFile file : files) {
            if (!paths.add(file.relativePath())) {
                throw new IllegalArgumentException("duplicate generated path: " + file.relativePath());
            }
        }
    }

    public GeneratedTree toTree() {
        GeneratedTree tree = new GeneratedTree();
        files.forEach(tree::put);
        return tree;
    }
}
