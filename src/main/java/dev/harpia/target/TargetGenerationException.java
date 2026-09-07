package dev.harpia.target;

import dev.harpia.diag.SourceRef;
import java.util.Objects;

/** A user-facing target failure whose stable diagnostic replaces a raw stack trace. */
public final class TargetGenerationException extends RuntimeException {

    private final String code;
    private final SourceRef source;

    public TargetGenerationException(
            String code, String message, SourceRef source, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.source = Objects.requireNonNull(source, "source");
    }

    public String code() {
        return code;
    }

    public SourceRef source() {
        return source;
    }
}
