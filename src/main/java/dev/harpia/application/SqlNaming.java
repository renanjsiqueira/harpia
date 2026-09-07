package dev.harpia.application;

import java.util.Locale;
import java.util.Objects;

/**
 * Relational naming for the Application IR.
 *
 * <p>This is not a Java concern: a relational schema is described the same way whichever target
 * generates the code for it, so the name is decided once, here, and every target reuses it.
 */
public final class SqlNaming {

    private SqlNaming() {
    }

    public static String identifier(String harpiaName) {
        Objects.requireNonNull(harpiaName, "harpiaName");
        return harpiaName
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}
