package dev.harpia;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One compilation request.
 *
 * <p>{@link Mode} does not change how a specification is compiled — validate and build run the very
 * same pipeline, which is what prevents "validate passes but build breaks". It only says whether
 * the caller intends to produce files, which decides the severity of an unsupported target.
 */
public record CompileRequest(Path projectRoot, Mode mode) {

    public CompileRequest {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(mode, "mode");
    }

    public CompileRequest(Path projectRoot) {
        this(projectRoot, Mode.BUILD);
    }

    public enum Mode {
        VALIDATE,
        BUILD
    }
}
