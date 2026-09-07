package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One fully resolved application operation, independent from parser and Business IR classes. */
public record ApplicationOperation(
        String title,
        String methodName,
        Kind kind,
        Endpoint endpoint,
        Optional<String> requestTypeName,
        List<ApplicationField> input,
        List<FlowInstruction> flow,
        Map<String, VariableType> variables,
        Result result,
        List<Failure> failures,
        boolean transactional,
        SourceRef where) {

    public ApplicationOperation {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(requestTypeName, "requestTypeName");
        input = List.copyOf(input);
        flow = List.copyOf(flow);
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        Objects.requireNonNull(result, "result");
        failures = List.copyOf(failures);
        Objects.requireNonNull(where, "where");
        if (requestTypeName.isPresent() != !input.isEmpty()) {
            throw new IllegalArgumentException("request type must exist exactly when input exists");
        }
    }

    public enum Kind {
        CREATE,
        READ,
        LIST,
        UPDATE,
        DELETE
    }

    public record Endpoint(HttpMethod method, String path, boolean hasIdPathVariable, Access access) {
        public Endpoint {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(access, "access");
        }
    }

    public enum HttpMethod {
        GET,
        POST,
        PUT,
        DELETE
    }

    public enum Access {
        PUBLIC
    }

    public record FlowInstruction(
            FlowCommand command,
            Optional<String> variable,
            Optional<String> entity,
            SourceRef where) {

        public FlowInstruction {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(where, "where");
            boolean valid = switch (command) {
                case VALIDATE_INPUT -> variable.isEmpty() && entity.isEmpty();
                case CREATE_FROM, LOAD_BY_ID, LIST_ALL -> variable.isPresent() && entity.isPresent();
                case UPDATE_FROM, SAVE, DELETE -> variable.isPresent() && entity.isEmpty();
                case RETURN -> entity.isEmpty();
            };
            if (!valid) {
                throw new IllegalArgumentException("invalid operands for flow command " + command);
            }
        }
    }

    public enum FlowCommand {
        VALIDATE_INPUT,
        CREATE_FROM,
        LOAD_BY_ID,
        UPDATE_FROM,
        LIST_ALL,
        SAVE,
        DELETE,
        RETURN
    }

    public record VariableType(VariableKind kind, String entity) {
        public VariableType {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(entity, "entity");
        }
    }

    public enum VariableKind {
        ENTITY,
        LIST
    }

    public record Result(int status, ResultKind kind, Optional<String> responseTypeName) {
        public Result {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(responseTypeName, "responseTypeName");
            if ((kind == ResultKind.NOTHING) == responseTypeName.isPresent()) {
                throw new IllegalArgumentException(
                        "response type must exist exactly for entity and list results");
            }
        }
    }

    public enum ResultKind {
        ENTITY,
        LIST,
        NOTHING
    }

    public record Failure(
            FailureCondition condition,
            Optional<String> field,
            int status,
            SourceRef where) {
        public Failure {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(where, "where");
        }
    }

    public enum FailureCondition {
        INVALID_INPUT,
        DUPLICATE,
        NOT_FOUND
    }
}
