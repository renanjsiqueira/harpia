package dev.harpia.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A relevant CommonMark block plus its exact source text. */
public record BlockNode(Kind kind, RawSpan raw, int headingLevel, int listDepth, String info) {

    public enum Kind {
        HEADING,
        LIST_ITEM,
        FENCED_CODE,
        PARAGRAPH
    }

    public BlockNode {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(info, "info");
        if (headingLevel < 0 || headingLevel > 6) {
            throw new IllegalArgumentException("headingLevel must be between 0 and 6");
        }
        if (listDepth < 0) {
            throw new IllegalArgumentException("listDepth must not be negative");
        }
        if (kind == Kind.HEADING && headingLevel == 0) {
            throw new IllegalArgumentException("a heading requires its level");
        }
        if (kind != Kind.HEADING && headingLevel != 0) {
            throw new IllegalArgumentException("only headings have a level");
        }
    }

    public static BlockNode heading(RawSpan raw, int level) {
        return new BlockNode(Kind.HEADING, raw, level, 0, "");
    }

    public static BlockNode listItem(RawSpan raw, int depth) {
        return new BlockNode(Kind.LIST_ITEM, raw, 0, depth, "");
    }

    public static BlockNode fencedCode(RawSpan raw, String info) {
        return new BlockNode(Kind.FENCED_CODE, raw, 0, 0, info == null ? "" : info);
    }

    public static BlockNode paragraph(RawSpan raw) {
        return new BlockNode(Kind.PARAGRAPH, raw, 0, 0, "");
    }

    /** Raw heading text after the ATX marker, with no inline Markdown interpretation. */
    public String headingText() {
        require(Kind.HEADING);
        String line = raw.text().lines().findFirst().orElse("");
        int index = 0;
        while (index < line.length() && index < 3 && line.charAt(index) == ' ') {
            index++;
        }
        int markerStart = index;
        while (index < line.length() && line.charAt(index) == '#') {
            index++;
        }
        if (index - markerStart != headingLevel
                || index >= line.length()
                || (line.charAt(index) != ' ' && line.charAt(index) != '\t')) {
            return raw.text().strip();
        }
        return line.substring(index).strip();
    }

    /** Raw list item after its bullet marker. Multiline items remain multiline. */
    public String listItemText() {
        require(Kind.LIST_ITEM);
        String value = raw.text();
        int newline = value.indexOf('\n');
        String first = newline < 0 ? value : value.substring(0, newline);
        int index = 0;
        while (index < first.length() && Character.isWhitespace(first.charAt(index))) {
            index++;
        }
        if (index >= first.length() || "-*+".indexOf(first.charAt(index)) < 0) {
            return value.strip();
        }
        index++;
        while (index < first.length() && (first.charAt(index) == ' ' || first.charAt(index) == '\t')) {
            index++;
        }
        String content = first.substring(index);
        return newline < 0 ? content.stripTrailing() : content + value.substring(newline);
    }

    /** Raw lines between the opening and closing fences, including their exact punctuation. */
    public List<RawSpan> fencedBodyLines() {
        require(Kind.FENCED_CODE);
        List<RawSpan> lines = raw.lines();
        if (lines.size() < 2) {
            return List.of();
        }
        int end = lines.size();
        RawSpan last = lines.getLast();
        if (isClosingFence(last.text())) {
            end--;
        }
        List<RawSpan> body = new ArrayList<>();
        for (int index = 1; index < end; index++) {
            body.add(lines.get(index));
        }
        return List.copyOf(body);
    }

    private boolean isClosingFence(String line) {
        String stripped = line.stripLeading();
        if (stripped.length() < 3) {
            return false;
        }
        char marker = stripped.charAt(0);
        if (marker != '`' && marker != '~') {
            return false;
        }
        int count = 0;
        while (count < stripped.length() && stripped.charAt(count) == marker) {
            count++;
        }
        return count >= 3 && stripped.substring(count).isBlank();
    }

    private void require(Kind expected) {
        if (kind != expected) {
            throw new IllegalStateException("operation requires " + expected + " but was " + kind);
        }
    }
}
