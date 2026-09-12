package dev.harpia.binding;

import dev.harpia.diag.SourceRef;
import dev.harpia.model.HttpBinding;
import dev.harpia.model.IntegrationHttpBinding;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolved, target-independent bindings keyed by the operation symbol they expose. */
public record BindingModel(
        Map<String, Http> http,
        Map<String, IntegrationHttp> integrationHttp) {

    public BindingModel {
        Objects.requireNonNull(http, "http");
        http = Collections.unmodifiableMap(new LinkedHashMap<>(http));
        Objects.requireNonNull(integrationHttp, "integrationHttp");
        integrationHttp = Collections.unmodifiableMap(new LinkedHashMap<>(integrationHttp));
    }

    public BindingModel(Map<String, Http> http) {
        this(http, Map.of());
    }

    public Optional<IntegrationHttpBinding> integrationBindingFor(String target) {
        Objects.requireNonNull(target, "target");
        return Optional.ofNullable(integrationHttp.get(target)).map(IntegrationHttp::binding);
    }

    public Optional<HttpBinding> bindingFor(String operation) {
        Objects.requireNonNull(operation, "operation");
        return Optional.ofNullable(http.get(operation)).map(Http::binding);
    }

    /** A resolved outbound HTTP binding plus its source locations. */
    public record IntegrationHttp(
            String target,
            IntegrationHttpBinding binding,
            SourceRef where,
            SourceRef endpointWhere) {
        public IntegrationHttp {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(endpointWhere, "endpointWhere");
        }
    }

    /** A resolved exposure plus both declaration and endpoint source locations. */
    public record Http(
            String operation,
            HttpBinding binding,
            Origin origin,
            SourceRef where,
            SourceRef endpointWhere) {
        public Http {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(endpointWhere, "endpointWhere");
        }
    }

    public enum Origin {
        INLINE,
        EXTERNAL
    }
}
