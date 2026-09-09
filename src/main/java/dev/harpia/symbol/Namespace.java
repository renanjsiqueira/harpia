package dev.harpia.symbol;

/**
 * Where a declared name lives.
 *
 * <p>Names are unique inside a namespace and independent across namespaces, so an entity and a
 * computation may share a name without either reference becoming ambiguous.
 */
public enum Namespace {
    TYPES("types", "Entity and Enum, and later Value"),
    OPERATIONS("operations", "use cases, and later Command and Query"),
    COMPUTATIONS("computations", "Logic, and later Formula and Decision"),
    TESTS("tests", "Scenario");

    private final String id;
    private final String description;

    Namespace(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }
}
