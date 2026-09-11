package dev.harpia.capability;

/** Technical needs inferred from Business IR without selecting an implementation. */
public enum Capability {
    HTTP("http"),
    PERSISTENCE("persistence"),
    EVENTS("events"),
    SECURITY("security"),
    CUSTOM("custom");

    private final String id;

    Capability(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
