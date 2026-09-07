package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.Access;
import java.util.Optional;

/** V0 only supports public endpoints. */
public final class AccessParser {

    private AccessParser() {
    }

    public static Optional<Access> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        String value = raw.strip();
        if (value.equals("public")) {
            return Optional.of(Access.PUBLIC);
        }
        UnsupportedFeatureDetector.reportAccess(value, diagnostics, where);
        return Optional.empty();
    }
}
