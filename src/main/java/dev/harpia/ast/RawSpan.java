package dev.harpia.ast;

import dev.harpia.diag.SourceRef;
import dev.harpia.source.SourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;

/** Text recovered directly from the normalized source, never from CommonMark inline nodes. */
public record RawSpan(String file, int line, int column, String text) {

    public RawSpan {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(text, "text");
        if (file.isBlank() || file.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("file must be a non-blank '/' path");
        }
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("line and column must be 1-indexed");
        }
    }

    /**
     * Where this block begins and ends. The end is exclusive and derived from the recovered text,
     * so it costs nothing and every diagnostic anchored on a block gains a span.
     */
    public SourceRef where() {
        String[] lines = text.split("\n", -1);
        int endLine = line + lines.length - 1;
        int endColumn = (lines.length == 1 ? column : 1)
                + lines[lines.length - 1].codePointCount(0, lines[lines.length - 1].length());
        return SourceRef.span(file, line, column, endLine, endColumn);
    }

    /**
     * Recovers a block node from its CommonMark source spans. Source spans use zero-based line and
     * column indexes and exclude line terminators, so separate spans are joined with canonical LF.
     */
    public static RawSpan from(SourceFile source, Node node) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(node, "node");
        List<SourceSpan> spans = node.getSourceSpans();
        if (spans.isEmpty()) {
            throw new IllegalArgumentException("CommonMark block has no source span: "
                    + node.getClass().getSimpleName());
        }

        String[] lines = source.content().split("\\n", -1);
        StringBuilder raw = new StringBuilder();
        SourceSpan first = spans.getFirst();
        int previousLine = -1;
        for (SourceSpan span : spans) {
            int lineIndex = span.getLineIndex();
            if (lineIndex < 0 || lineIndex >= lines.length) {
                throw new IllegalArgumentException("source span line is outside the source");
            }
            int start = span.getColumnIndex();
            int end = start + span.getLength();
            if (start < 0 || end < start || end > lines[lineIndex].length()) {
                throw new IllegalArgumentException("source span column is outside the source");
            }
            if (previousLine >= 0) {
                raw.append('\n');
                for (int omitted = previousLine + 1; omitted < lineIndex; omitted++) {
                    raw.append('\n');
                }
            }
            raw.append(lines[lineIndex], start, end);
            previousLine = lineIndex;
        }
        return new RawSpan(
                source.relativePath(),
                first.getLineIndex() + 1,
                first.getColumnIndex() + 1,
                raw.toString());
    }

    /** Returns each physical source line with its original 1-indexed position. */
    public List<RawSpan> lines() {
        String[] values = text.split("\\n", -1);
        List<RawSpan> result = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            result.add(new RawSpan(file, line + index, index == 0 ? column : 1, values[index]));
        }
        return List.copyOf(result);
    }
}
