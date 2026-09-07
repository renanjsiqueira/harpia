package dev.harpia.inspect;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A stage of the compiler pipeline that can be shown.
 *
 * <p>These are the architectural boundaries of the project. Being able to print each one is what
 * makes a claim about them checkable from outside the test suite: if a framework name ever reaches
 * a layer that should not know one, {@code harpia inspect} shows it.
 */
public enum Stage {
    AST("ast", "the Harpia syntax tree, one module per file"),
    SYMBOLS("symbols", "every name the project declares, by namespace"),
    BUSINESS_IR("business-ir", "business meaning, independent of any target"),
    APPLICATION_IR("application-ir", "application behaviour, still independent of any target");

    private final String id;
    private final String description;

    Stage(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public static Optional<Stage> find(String id) {
        return Arrays.stream(values())
                .filter(stage -> stage.id.equals(id.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static String ids() {
        return Arrays.stream(values()).map(Stage::id).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
