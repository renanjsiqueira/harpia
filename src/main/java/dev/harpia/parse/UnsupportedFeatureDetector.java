package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import java.util.Set;

/** Explicitly rejects syntax that belongs to a planned post-MVP capability. */
public final class UnsupportedFeatureDetector {

    private static final Set<String> UNSUPPORTED_SECTIONS = Set.of("Events", "Email", "Security");
    private static final String SCOPE_HINT = "see docs/requirements.md section 5 (Fora do escopo)";

    private UnsupportedFeatureDetector() {
    }

    /**
     * Whether the line uses the explicit relationship arrow.
     *
     * <p>A {@code List<...>} is no longer refused here: whether its element is a scalar, a declared
     * enum or an entity is a question only the symbol table can answer, so a collection of entities
     * is refused during semantic validation instead.
     */
    public static boolean relationship(String fieldLine) {
        return fieldLine.contains("->");
    }

    public static void reportRelationship(DiagnosticCollector diagnostics, SourceRef where) {
        diagnostics.error(
                ErrorCodes.UNSUPPORTED_RELATIONSHIP,
                "entity relationships are not supported by Harpia V0",
                where,
                SCOPE_HINT);
    }

    public static boolean unsupportedSection(String heading) {
        return UNSUPPORTED_SECTIONS.contains(heading);
    }

    public static void reportSection(
            String heading, DiagnosticCollector diagnostics, SourceRef where) {
        diagnostics.error(
                ErrorCodes.UNSUPPORTED_EMAIL_OR_EVENTS,
                "section '" + heading + "' is not supported by Harpia V0",
                where,
                SCOPE_HINT);
    }

    public static void reportAccess(String access, DiagnosticCollector diagnostics, SourceRef where) {
        diagnostics.error(
                ErrorCodes.UNSUPPORTED_AUTHENTICATION,
                "access '" + access + "' is not supported; authentication is outside Harpia V0",
                where,
                SCOPE_HINT);
    }
}
