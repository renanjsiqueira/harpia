package dev.harpia.target.javaspring.model;

/** Modifier keywords supported by the generated Java subset, in rendering order. */
public enum JavaModifier {
    ABSTRACT("abstract"),
    STATIC("static"),
    FINAL("final");

    private final String keyword;

    JavaModifier(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }
}
