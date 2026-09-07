package dev.harpia.model;

import java.util.Objects;

/** Canonical Harpia name conversion. It knows no target language and no database. */
public final class Naming {

    private Naming() {
    }

    public static String useCaseBaseName(String title) {
        Objects.requireNonNull(title, "title");
        return title.replace(" ", "");
    }
}
