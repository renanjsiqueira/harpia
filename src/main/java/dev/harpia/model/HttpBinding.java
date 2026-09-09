package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;

public record HttpBinding(
        HttpMethod method,
        String baseUrl,
        String path,
        boolean hasIdPathVariable,
        AccessRule access,
        List<RequestMapping> request,
        ResponseMapping response,
        SourceRef where,
        SourceRef endpointWhere) {
    public HttpBinding {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(access, "access");
        request = List.copyOf(request);
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(endpointWhere, "endpointWhere");
    }

    public String effectivePath() {
        return baseUrl.isEmpty() || baseUrl.equals("/") ? path : baseUrl + path;
    }

    public sealed interface RequestMapping permits Path, Query, Header, Body {
        String input();

        SourceRef where();
    }

    public record Path(String input, String parameter, SourceRef where)
            implements RequestMapping {
        public Path {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(parameter, "parameter");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Query(String input, String parameter, SourceRef where)
            implements RequestMapping {
        public Query {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(parameter, "parameter");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Header(String input, String header, SourceRef where)
            implements RequestMapping {
        public Header {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Body(String input, SourceRef where) implements RequestMapping {
        public Body {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(where, "where");
        }
    }

    public sealed interface ResponseMapping permits ResponseBody, NoResponse {
        SourceRef where();
    }

    public record ResponseBody(String output, SourceRef where) implements ResponseMapping {
        public ResponseBody {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(where, "where");
        }
    }

    public record NoResponse(SourceRef where) implements ResponseMapping {
        public NoResponse {
            Objects.requireNonNull(where, "where");
        }
    }

    public enum HttpMethod {
        GET,
        POST,
        PUT,
        DELETE
    }
}
