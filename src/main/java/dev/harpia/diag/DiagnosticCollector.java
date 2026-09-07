package dev.harpia.diag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Accumulates diagnostics during one compilation and hands them back already ordered, so no caller
 * ever has to sort.
 *
 * <p>Not thread-safe by design: one collector belongs to one {@code compile} call. The compiler
 * itself stays stateless and reusable.
 */
public final class DiagnosticCollector {

    private final List<Diagnostic> items = new ArrayList<>();

    public void add(Diagnostic diagnostic) {
        items.add(Objects.requireNonNull(diagnostic, "diagnostic"));
    }

    public void addAll(Collection<Diagnostic> diagnostics) {
        diagnostics.forEach(this::add);
    }

    public void error(String code, String message) {
        add(Diagnostic.error(code, message));
    }

    public void error(String code, String message, SourceRef where) {
        add(Diagnostic.error(code, message, where));
    }

    public void error(String code, String message, SourceRef where, String hint) {
        add(Diagnostic.error(code, message, where, hint));
    }

    public void warning(String code, String message) {
        add(Diagnostic.warning(code, message));
    }

    public void warning(String code, String message, SourceRef where) {
        add(Diagnostic.warning(code, message, where));
    }

    public void warning(String code, String message, SourceRef where, String hint) {
        add(Diagnostic.warning(code, message, where, hint));
    }

    public boolean hasErrors() {
        return items.stream().anyMatch(Diagnostic::isError);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    /** Immutable, ordered snapshot. */
    public List<Diagnostic> diagnostics() {
        return DiagnosticOrdering.sorted(items);
    }
}
