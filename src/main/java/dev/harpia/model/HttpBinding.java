package dev.harpia.model;

import java.util.Objects;

public record HttpBinding(HttpMethod method, String path, boolean hasIdPathVariable) {
    public HttpBinding {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
    }

    public enum HttpMethod {
        GET,
        POST,
        PUT,
        DELETE
    }
}
