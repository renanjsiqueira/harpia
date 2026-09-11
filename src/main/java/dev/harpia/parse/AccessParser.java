package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.Access;
import java.util.Optional;

/**
 * The one line inside {@code ### Access}.
 *
 * <p>V0 has only {@code public}. {@code authenticated} is V1, and is refused there by the validator
 * that knows the language version rather than here, where only the word is visible.
 */
public final class AccessParser {

    private AccessParser() {
    }

    public static Optional<Access> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        String value = raw.strip();
        if (value.equals("public")) {
            return Optional.of(Access.PUBLIC);
        }
        if (value.equals("authenticated")) {
            return Optional.of(Access.AUTHENTICATED);
        }
        UnsupportedFeatureDetector.reportAccess(value, diagnostics, where);
        return Optional.empty();
    }
}
