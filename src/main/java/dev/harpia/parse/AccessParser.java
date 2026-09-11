package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.Access;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one line inside {@code ### Access}.
 *
 * <p>V0 has only {@code public}. {@code authenticated} is V1, and is refused there by the validator
 * that knows the language version rather than here, where only the word is visible.
 */
public final class AccessParser {

    /** A role reads like the specification's other names: lower_snake_case. */
    private static final Pattern ROLES = Pattern.compile(
            "^role ([a-z][a-z0-9_]*(?: or [a-z][a-z0-9_]*)*)$");

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
        Matcher roles = ROLES.matcher(value);
        if (roles.matches()) {
            // Listing several is how you say a thing is open to more than one kind of person, so
            // `or` reads the rule out loud and the check is "any of".
            return Optional.of(Access.role(List.of(roles.group(1).split(" or "))));
        }
        UnsupportedFeatureDetector.reportAccess(value, diagnostics, where);
        return Optional.empty();
    }
}
