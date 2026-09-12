package dev.harpia.parse;

import dev.harpia.ast.RawSpan;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.SourceRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How Harpia reads indentation, in one place.
 *
 * <p>Four spaces, never a tab, and a block never starts indented. These are rules of the language
 * rather than of any one block kind, so Logic and Flow read them from here instead of each keeping
 * a copy that could drift.
 */
public final class IndentedLines {

    private static final int INDENT = 4;

    private IndentedLines() {
    }

    /** One non-blank line, with its indentation resolved to a nesting level. */
    public record Line(int level, String text, RawSpan span, int indent) {

        public Line {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(span, "span");
        }

        public SourceRef where() {
            return span.where();
        }

        /** Points at the first character of the statement rather than at the indentation. */
        public SourceRef contentWhere() {
            return LineSyntax.at(span.where(), span.text(), indent);
        }
    }

    /**
     * @param emptyMessage what to say when the block has no statements at all
     * @param indentedMessage what to say when the block opens indented
     */
    public static Optional<List<Line>> scan(
            List<RawSpan> bodyLines,
            SourceRef blockWhere,
            String indentCode,
            String emptyCode,
            String emptyMessage,
            String indentedMessage,
            String tabMessage,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(bodyLines, "bodyLines");
        Objects.requireNonNull(blockWhere, "blockWhere");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<Line> lines = new ArrayList<>();
        boolean valid = true;
        for (RawSpan span : bodyLines) {
            String text = span.text().stripTrailing();
            if (text.isBlank()) {
                continue;
            }
            int tab = text.indexOf('\t');
            if (tab >= 0) {
                diagnostics.error(
                        indentCode, tabMessage, LineSyntax.at(span.where(), text, tab));
                valid = false;
                continue;
            }
            int indent = 0;
            while (indent < text.length() && text.charAt(indent) == ' ') {
                indent++;
            }
            if (indent % INDENT != 0) {
                diagnostics.error(
                        indentCode,
                        "indentation must be a multiple of four spaces but was " + indent,
                        span.where());
                valid = false;
                continue;
            }
            lines.add(new Line(indent / INDENT, text.substring(indent), span, indent));
        }
        if (!valid) {
            return Optional.empty();
        }
        if (lines.isEmpty()) {
            diagnostics.error(emptyCode, emptyMessage, blockWhere);
            return Optional.empty();
        }
        if (lines.getFirst().level() != 0) {
            diagnostics.error(indentCode, indentedMessage, lines.getFirst().where());
            return Optional.empty();
        }
        return Optional.of(List.copyOf(lines));
    }
}
