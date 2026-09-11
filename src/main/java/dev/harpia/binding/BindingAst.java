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

    public List<IntegrationHttp> integrationHttpBindings() {
        return declarations.stream()
                .filter(IntegrationHttp.class::isInstance)
                .map(IntegrationHttp.class::cast)
                .toList();
    }

    /** Closed binding kinds; messaging and persistence can add nodes without changing the file. */
    public sealed interface Declaration permits Http, IntegrationHttp {
        SourceRef where();
    }

    /** One outbound HTTP adapter binding for an Integration operation. */
    public record IntegrationHttp(
            String integration,
            String operation,
            Endpoint endpoint,
            List<RequestMapping> request,
            ResponseMapping response,
            SourceRef where)
            implements Declaration {
        public IntegrationHttp {
            Objects.requireNonNull(integration, "integration");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(endpoint, "endpoint");
            request = List.copyOf(request);
            Objects.requireNonNull(response, "response");
            Objects.requireNonNull(where, "where");
        }

        public String target() {
            return integration + "." + operation;
        }
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

    /**
     * Who may reach an operation.
     *
     * <p>{@code AUTHENTICATED} says the request must carry an identity. {@code ROLE} says the
     * identity must also hold one of the named roles — any of them, because listing several is
     * how you say a thing is open to more than one kind of person.
     *
     * <p>How an identity is proved, and where its roles come from, is a provider's decision. What
     * is declared here is which endpoints are reachable without one.
     */
    public record Access(Kind kind, java.util.List<String> roles) {

        public enum Kind {
            PUBLIC,
            AUTHENTICATED,
            ROLE,
            SCOPE
        }

        public static final Access PUBLIC = new Access(Kind.PUBLIC, java.util.List.of());
        public static final Access AUTHENTICATED =
                new Access(Kind.AUTHENTICATED, java.util.List.of());

        public Access {
            java.util.Objects.requireNonNull(kind, "kind");
            roles = java.util.List.copyOf(roles);
            if (roles.isEmpty() != (kind == Kind.PUBLIC || kind == Kind.AUTHENTICATED)) {
                throw new IllegalArgumentException(
                        "ROLE and SCOPE name what they demand; nothing else does");
            }
        }

        public static Access role(java.util.List<String> roles) {
            return new Access(Kind.ROLE, roles);
        }

        public static Access scope(java.util.List<String> scopes) {
            return new Access(Kind.SCOPE, scopes);
        }

        /** True when the request has to carry an identity at all, whatever is asked of it. */
        public boolean requiresIdentity() {
            return kind != Kind.PUBLIC;
        }

        /**
         * The stable form the inspect stages print.
         *
         * <p>{@code PUBLIC} and {@code AUTHENTICATED} read as they did when this was an enum, so
         * nothing that was already written down changed meaning.
         */
        @Override
        public String toString() {
            return roles.isEmpty()
                    ? kind.name()
                    : kind.name() + " " + String.join(" or ", roles);
        }
    }
}
