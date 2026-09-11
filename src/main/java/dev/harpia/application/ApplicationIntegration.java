package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Generator-facing outbound port with an optional provider-neutral transport binding. */
public record ApplicationIntegration(String name, List<Operation> operations, SourceRef where) {
    public ApplicationIntegration {
        Objects.requireNonNull(name, "name");
        operations = List.copyOf(operations);
        Objects.requireNonNull(where, "where");
    }

    public record Operation(
            String name,
            List<Parameter> input,
            Result output,
            List<Failure> errors,
            Optional<Http> http,
            SourceRef where) {
        public Operation {
            Objects.requireNonNull(name, "name");
            input = List.copyOf(input);
            Objects.requireNonNull(output, "output");
            errors = List.copyOf(errors);
            Objects.requireNonNull(http, "http");
            Objects.requireNonNull(where, "where");
        }
    }

    /** Resolved outbound HTTP transport, independent of its target implementation. */
    public record Http(
            ApplicationOperation.HttpMethod method,
            String baseUrl,
            String path,
            List<ApplicationOperation.RequestMapping> request,
            ApplicationOperation.ResponseMapping response,
            SourceRef where,
            SourceRef endpointWhere) {
        public Http {
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

    public record Parameter(
            String name, ApplicationFieldType type, boolean required, SourceRef where) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    /** An empty type represents the explicit {@code nothing} result. */
    public record Result(java.util.Optional<ApplicationFieldType> type, SourceRef where) {
        public Result {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Failure(String name, SourceRef where) {
        public Failure {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }
}
