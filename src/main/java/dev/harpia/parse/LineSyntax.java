package dev.harpia.parse;

import dev.harpia.diag.SourceRef;

final class LineSyntax {

    private LineSyntax() {
    }

    static SourceRef at(SourceRef start, String raw, int charOffset) {
        int bounded = Math.max(0, Math.min(charOffset, raw.length()));
        int codePoints = raw.codePointCount(0, bounded);
        return SourceRef.of(start.file(), start.line(), start.column() + codePoints);
    }
}
