package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

/** Transport binding for one outbound Integration operation. */
public record IntegrationHttpBinding(
        HttpBinding.HttpMethod method,
        String baseUrl,
        String path,
        List<HttpBinding.RequestMapping> request,
        HttpBinding.ResponseMapping response,
        SourceRef where,
        SourceRef endpointWhere) {
    public IntegrationHttpBinding {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(path, "path");
        request = List.copyOf(request);
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(endpointWhere, "endpointWhere");
    }

    public String effectiveUrl() {
        return baseUrl.endsWith("/") && path.startsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + path
                : baseUrl + path;
    }
}
