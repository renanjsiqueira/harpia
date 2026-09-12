package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transport binding for one outbound Integration operation. */
public record IntegrationHttpBinding(
        HttpBinding.HttpMethod method,
        String baseUrl,
        String path,
        List<HttpBinding.RequestMapping> request,
        HttpBinding.ResponseMapping response,
        Optional<Auth> auth,
        SourceRef where,
        SourceRef endpointWhere) {

    /**
     * How the call proves who is calling, without saying with what.
     *
     * <p>The scheme travels with the transport because it is part of reaching the other side. The
     * credential does not travel at all: it differs per deployment and is a secret, so it stays
     * in configuration and never in a declaration anyone commits.
     */
    public record Auth(Kind kind, String header, SourceRef where) {

        public enum Kind { BEARER, API_KEY }

        public Auth {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(where, "where");
        }
    }

    public IntegrationHttpBinding(
            HttpBinding.HttpMethod method,
            String baseUrl,
            String path,
            List<HttpBinding.RequestMapping> request,
            HttpBinding.ResponseMapping response,
            SourceRef where,
            SourceRef endpointWhere) {
        this(method, baseUrl, path, request, response, Optional.empty(), where, endpointWhere);
    }

    public IntegrationHttpBinding {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(path, "path");
        request = List.copyOf(request);
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(endpointWhere, "endpointWhere");
    }

    public String effectiveUrl() {
        return baseUrl.endsWith("/") && path.startsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + path
                : baseUrl + path;
    }
}
