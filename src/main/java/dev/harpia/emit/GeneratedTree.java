package dev.harpia.emit;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Deterministically ordered generated files held entirely in memory. */
public final class GeneratedTree {

    private final TreeMap<String, GeneratedFile> files = new TreeMap<>();

    public void put(String relativePath, String content) {
        put(GeneratedFile.other(relativePath, content));
    }

    public void put(GeneratedFile file) {
        Objects.requireNonNull(file, "file");
        if (files.putIfAbsent(file.relativePath(), file) != null) {
            throw new IllegalArgumentException("generated path already exists: " + file.relativePath());
        }
    }

    public SortedMap<String, String> files() {
        TreeMap<String, String> content = new TreeMap<>();
        files.forEach((path, file) -> content.put(path, file.content()));
        return Collections.unmodifiableSortedMap(content);
    }

    public List<GeneratedFile> generatedFiles() {
        return List.copyOf(files.values());
    }

    public int size() {
        return files.size();
    }

    static void validatePath(String relativePath) {
        if (relativePath.isBlank() || relativePath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("generated path must be a non-blank '/' path");
        }
        try {
            Path path = Path.of(relativePath);
            if (path.isAbsolute()
                    || !path.normalize().toString().replace('\\', '/').equals(relativePath)
                    || path.startsWith("..")) {
                throw new IllegalArgumentException(
                        "generated path must be normalized and relative: " + relativePath);
            }
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("invalid generated path: " + relativePath, exception);
        }
    }
}
