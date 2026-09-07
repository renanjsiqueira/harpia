package dev.harpia.target.javaspring.model;

/** Visibility keywords supported by the generated Java subset. */
public enum JavaVisibility {
    PUBLIC("public"),
    PROTECTED("protected"),
    PRIVATE("private"),
    PACKAGE_PRIVATE("");

    private final String keyword;

    JavaVisibility(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }
}
