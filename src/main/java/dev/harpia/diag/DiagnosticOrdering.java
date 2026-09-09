package dev.harpia.diag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Total, stable ordering of diagnostics.
 *
 * <p>Diagnostics without a {@link SourceRef} come first, grouped by code. The remaining ones are
 * ordered by file, then line, then column, then code, then message.
 *
 * <p>Severity and hint are compared last. They are not part of the ordering rule in the contract,
 * which leaves ties open; comparing them makes the order <em>total</em>, so two runs over the same
 * multiset of diagnostics produce the exact same sequence regardless of insertion order.
 */
public final class DiagnosticOrdering {

    private DiagnosticOrdering() {
    }

    private static final Comparator<Diagnostic> WITHOUT_LOCATION_FIRST =
            Comparator.comparing(d -> d.where().isPresent() ? 1 : 0);

    private static final Comparator<Diagnostic> BY_LOCATION =
            Comparator.comparing((Diagnostic d) -> d.where().map(SourceRef::file).orElse(""))
                    .thenComparingInt(d -> d.where().map(SourceRef::line).orElse(0))
                    .thenComparingInt(d -> d.where().map(SourceRef::column).orElse(0));

    /** The single comparator every printer and collector uses. */
    public static final Comparator<Diagnostic> COMPARATOR =
            WITHOUT_LOCATION_FIRST
                    .thenComparing(BY_LOCATION)
                    .thenComparing(Diagnostic::code)
                    .thenComparing(Diagnostic::message)
                    .thenComparing(d -> d.severity().name())
                    .thenComparing(d -> d.hint().orElse(""))
                    .thenComparing(DiagnosticOrdering::relatedKey);

    /** Related locations join the tie-break so the order stays total. */
    private static String relatedKey(Diagnostic diagnostic) {
        return diagnostic.related().stream()
                .sorted()
                .map(related -> related.where().describe() + " " + related.message())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    /** Returns an immutable, sorted copy. The input list is not modified. */
    public static List<Diagnostic> sorted(Collection<Diagnostic> diagnostics) {
        List<Diagnostic> copy = new ArrayList<>(diagnostics);
        copy.sort(COMPARATOR);
        return List.copyOf(copy);
    }

    /** Exposed for tests that want to assert the grouping rule directly. */
    public static boolean hasLocation(Diagnostic diagnostic) {
        Optional<SourceRef> where = diagnostic.where();
        return where.isPresent();
    }
}
