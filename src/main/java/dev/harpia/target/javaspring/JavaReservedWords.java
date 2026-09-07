package dev.harpia.target.javaspring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Words Java reserves.
 *
 * <p>This is a constraint of one target, not of Harpia: a name that is illegal in Java may be
 * perfectly legal in another target's language. The check therefore runs during target validation
 * and never during semantic analysis.
 */
final class JavaReservedWords {

    static final Set<String> ALL = load("/reserved-java.txt");

    private JavaReservedWords() {
    }

    private static Set<String> load(String resource) {
        InputStream stream = JavaReservedWords.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new ExceptionInInitializerError("missing resource " + resource);
        }
        LinkedHashSet<String> words = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(words::add);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Set.copyOf(words);
    }
}
