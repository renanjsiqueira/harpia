package dev.harpia.model;

import java.util.Locale;
import java.util.Objects;

/** Canonical Harpia name conversion. It knows no target language and no database. */
public final class Naming {

    private Naming() {
    }

    public static String useCaseBaseName(String title) {
        Objects.requireNonNull(title, "title");
        return title.replace(" ", "");
    }

    /**
     * The canonical symbol for a declared domain error: {@code insufficient balance} becomes
     * {@code InsufficientBalance}. What a target then calls the type is the target's business.
     */
    public static String errorSymbol(String condition) {
        Objects.requireNonNull(condition, "condition");
        StringBuilder symbol = new StringBuilder();
        for (String word : condition.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            symbol.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return symbol.toString();
    }
}
