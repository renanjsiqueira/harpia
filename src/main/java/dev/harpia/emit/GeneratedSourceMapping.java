package dev.harpia.emit;

import dev.harpia.diag.SourceRef;
import java.util.Objects;

/** Maps one generated symbol and line range back to the Harpia source that produced it. */
public record GeneratedSourceMapping(String symbol, SourceRef source, SourceRef generated)
        implements Comparable<GeneratedSourceMapping> {

    public GeneratedSourceMapping {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(generated, "generated");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("generated symbol must not be blank");
        }
        if (!generated.hasPosition() || generated.end().isEmpty()) {
            throw new IllegalArgumentException(
                    "generated mapping must have a positioned range: " + generated.describe());
        }
    }

    @Override
    public int compareTo(GeneratedSourceMapping other) {
        int byFile = generated.file().compareTo(other.generated.file());
        if (byFile != 0) {
            return byFile;
        }
        int byLine = Integer.compare(generated.line(), other.generated.line());
        if (byLine != 0) {
            return byLine;
        }
        int byColumn = Integer.compare(generated.column(), other.generated.column());
        if (byColumn != 0) {
            return byColumn;
        }
        return symbol.compareTo(other.symbol());
    }
}
