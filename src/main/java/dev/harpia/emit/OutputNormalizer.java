package dev.harpia.emit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonicalizes every emitted text file before it enters the generated tree. */
public final class OutputNormalizer {

    private OutputNormalizer() {
    }

    public static String normalize(String source) {
        Objects.requireNonNull(source, "source");
        String normalized = source.startsWith("\uFEFF") ? source.substring(1) : source;
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');

        String[] rawLines = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>();
        boolean previousBlank = false;
        for (String rawLine : rawLines) {
            String line = rawLine.stripTrailing();
            boolean blank = line.isEmpty();
            if (blank && previousBlank) {
                continue;
            }
            lines.add(line);
            previousBlank = blank;
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return String.join("\n", lines) + "\n";
    }
}
