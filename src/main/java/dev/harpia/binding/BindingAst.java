package dev.harpia.binding;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Syntax tree of one external binding source. */
public record BindingAst(
        String file,
        Optional<BaseUrl> baseUrl,
        List<Declaration> declarations,
        SourceRef where) {

    public BindingAst {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(baseUrl, "baseUrl");
        declarations = List.copyOf(declarations);
        Objects.requireNonNull(where, "where");
    }

    public List<Http> httpBindings() {
        return declarations.stream().filter(Http.class::isInstance).map(Http.class::cast).toList();
    }

    /** Closed binding kinds; messaging and persistence can add nodes without changing the file. */
    public sealed interface Declaration permits Http {
        SourceRef where();
    }

    /** One HTTP exposure referring to an operation symbol by its canonical name. */
    public record Http(
            String operation,
            Endpoint endpoint,
            Access access,
            List<RequestMapping> request,
            ResponseMapping response,
            SourceRef where)
            implements Declaration {
        public Http {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(access, "access");
            request = List.copyOf(request);
            Objects.requireNonNull(response, "response");
            Objects.requireNonNull(where, "where");
        }
    }

    /** Optional path prefix applied to every endpoint in this binding file. */
    public record BaseUrl(String path, SourceRef where) {
        public BaseUrl {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(where, "where");
        }
    }

    public record Endpoint(String method, String path, SourceRef where) {
        public Endpoint {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(where, "where");
        }
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

    public enum Access {
        PUBLIC
    }
}
