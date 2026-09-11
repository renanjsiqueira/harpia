package dev.harpia.application;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.logic.TypedExpression;
import dev.harpia.model.OperationNature;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One fully resolved application operation.
 *
 * <p>{@code nature} is what the specification declared. {@code kind} is the CRUD shape derived from
 * the flow, which is all a V0 heading gives us. Keeping both means a target can honour a stated
 * intent instead of re-deriving it, and can still fall back to the shape when nothing was stated.
 */
public record ApplicationOperation(
        String title,
        String methodName,
        OperationNature nature,
        Kind kind,
        Optional<Endpoint> endpoint,
        Optional<String> requestTypeName,
        List<ApplicationField> input,
        List<ApplicationRule> rules,
        List<FlowInstruction> flow,
        Map<String, VariableType> variables,
        Result result,
        List<Failure> failures,
        boolean transactional,
        SourceRef where) {

    public ApplicationOperation {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(nature, "nature");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(requestTypeName, "requestTypeName");
        input = List.copyOf(input);
        rules = List.copyOf(rules);
        flow = List.copyOf(flow);
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        Objects.requireNonNull(result, "result");
        failures = List.copyOf(failures);
        Objects.requireNonNull(where, "where");
        if (requestTypeName.isPresent() != !input.isEmpty()) {
            throw new IllegalArgumentException("request type must exist exactly when input exists");
        }
    }

    /**
     * Whether an update applies only the fields the request actually carried.
     *
     * <p>A full update states the whole resource, so a field the request left out is a field set to
     * nothing. A partial update states only the changes, so a field left out is not a change at
     * all and the stored value stands. Only a binding can say which of the two was meant, because
     * only a binding says how the request arrives.
     */
    public boolean partialUpdate() {
        return endpoint.map(exposed -> exposed.method() == HttpMethod.PATCH).orElse(false);
    }

    /** Whether the semantic flow needs the entity identifier, independently of any binding. */
    public boolean requiresId() {
        return allInstructions().stream()
                .anyMatch(instruction -> instruction.command() == FlowCommand.LOAD_BY_ID);
    }

    /** Every instruction in source order, including instructions in conditional branches. */
    public List<FlowInstruction> allInstructions() {
        java.util.ArrayList<FlowInstruction> result = new java.util.ArrayList<>();
        append(flow, result);
        return List.copyOf(result);
    }

    private static void append(List<FlowInstruction> source, List<FlowInstruction> target) {
        for (FlowInstruction instruction : source) {
            target.add(instruction);
            if (instruction.command() == FlowCommand.IF) {
                append(instruction.whenTrue(), target);
                append(instruction.whenFalse(), target);
            }
        }
    }

    public enum Kind {
        CREATE,
        READ,
        LIST,
        UPDATE,
        DELETE
    }

    public record Endpoint(
            HttpMethod method,
            String baseUrl,
            String path,
            boolean hasIdPathVariable,
            Access access,
            List<RequestMapping> request,
            ResponseMapping response,
            SourceRef where,
            SourceRef endpointWhere) {
        public Endpoint {
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

        public boolean hasBody() {
            return request.stream().anyMatch(Body.class::isInstance);
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

    public enum HttpMethod {
        GET,
        POST,
        PUT,
        PATCH,
        DELETE
    }

    public enum Access {
        PUBLIC
    }

    public record FlowInstruction(
            FlowCommand command,
            Optional<String> variable,
            Optional<String> entity,
            List<String> fields,
            List<SortOrder> sort,
            boolean paged,
            Optional<TypedValue> value,
            Optional<Invocation> invocation,
            Optional<IntegrationInvocation> integrationInvocation,
            List<FlowInstruction> whenTrue,
            List<FlowInstruction> whenFalse,
            SourceRef where) {

        /** One ordering step: a field and whether it descends. */
        public record SortOrder(String field, boolean descending) {
            public SortOrder {
                Objects.requireNonNull(field, "field");
            }
        }

        /**
         * A name, the source it was written as, and the expression it was typed to.
         *
         * <p>A {@code fail} or {@code require} names the error and carries its condition; a
         * {@code set} names the field and carries the assigned value. The shape is the same because
         * the question is: which thing, written how, meaning what.
         */
        public record TypedValue(
                String name, String text, TypedExpression expression) {
            public TypedValue {
                Objects.requireNonNull(name, "name");
                Objects.requireNonNull(text, "text");
                Objects.requireNonNull(expression, "expression");
            }
        }

        /** A pure Logic invocation whose arguments already follow the declared signature. */
        public record Invocation(
                String target,
                List<Argument> arguments,
                LogicType resultType) {
            public Invocation {
                Objects.requireNonNull(target, "target");
                arguments = List.copyOf(arguments);
                Objects.requireNonNull(resultType, "resultType");
            }

            public record Argument(
                    String name,
                    TypedExpression value,
                    LogicType parameterType) {
                public Argument {
                    Objects.requireNonNull(name, "name");
                    Objects.requireNonNull(value, "value");
                    Objects.requireNonNull(parameterType, "parameterType");
                }
            }
        }

        /** A provider-independent call to one operation of an outbound Integration. */
        public record IntegrationInvocation(
                String integration,
                String operation,
                List<Argument> arguments,
                Optional<LogicType> resultType) {
            public IntegrationInvocation {
                Objects.requireNonNull(integration, "integration");
                Objects.requireNonNull(operation, "operation");
                arguments = List.copyOf(arguments);
                Objects.requireNonNull(resultType, "resultType");
            }

            public String target() {
                return integration + "." + operation;
            }

            public record Argument(
                    String name,
                    TypedExpression value,
                    LogicType parameterType) {
                public Argument {
                    Objects.requireNonNull(name, "name");
                    Objects.requireNonNull(value, "value");
                    Objects.requireNonNull(parameterType, "parameterType");
                }
            }
        }

        /** Every instruction but a conditional, which is the only one that carries branches. */
        public FlowInstruction(
                FlowCommand command,
                Optional<String> variable,
                Optional<String> entity,
                List<String> fields,
                List<SortOrder> sort,
                boolean paged,
                Optional<TypedValue> value,
                SourceRef where) {
            this(command, variable, entity, fields, sort, paged, value, Optional.empty(),
                    Optional.empty(), List.of(), List.of(), where);
        }

        /** An instruction that carries neither a typed expression nor conditional branches. */
        public FlowInstruction(
                FlowCommand command,
                Optional<String> variable,
                Optional<String> entity,
                SourceRef where) {
            this(command, variable, entity, List.of(), List.of(), false, Optional.empty(),
                    Optional.empty(), Optional.empty(), List.of(), List.of(), where);
        }

        public FlowInstruction {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            fields = List.copyOf(fields);
            sort = List.copyOf(sort);
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(invocation, "invocation");
            Objects.requireNonNull(integrationInvocation, "integrationInvocation");
            whenTrue = List.copyOf(whenTrue);
            whenFalse = List.copyOf(whenFalse);
            Objects.requireNonNull(where, "where");
            boolean valid = switch (command) {
                case VALIDATE_INPUT -> variable.isEmpty() && entity.isEmpty();
                case FAIL, REQUIRE -> value.isPresent() && variable.isEmpty() && entity.isEmpty();
                case FIND_BY, LIST_BY ->
                        variable.isPresent() && entity.isPresent() && !fields.isEmpty();
                // The assigned field is the value's own name, so it is not repeated in `fields`.
                case SET_FIELD, ADD_TO, REMOVE_FROM ->
                        variable.isPresent() && fields.isEmpty() && value.isPresent();
                case CALL_LOGIC -> variable.isPresent()
                        && entity.isEmpty()
                        && fields.isEmpty()
                        && sort.isEmpty()
                        && !paged
                        && value.isEmpty()
                        && invocation.isPresent();
                case CALL_INTEGRATION -> entity.isEmpty()
                        && fields.isEmpty()
                        && sort.isEmpty()
                        && !paged
                        && value.isEmpty()
                        && integrationInvocation.isPresent()
                        && (variable.isPresent()
                                == integrationInvocation.orElseThrow().resultType().isPresent());
                case IF -> variable.isEmpty()
                        && entity.isEmpty()
                        && fields.isEmpty()
                        && sort.isEmpty()
                        && !paged
                        && value.isPresent()
                        && !whenTrue.isEmpty();
                case CREATE_FROM, LOAD_BY_ID, LIST_ALL -> variable.isPresent() && entity.isPresent();
                case UPDATE_FROM, SAVE, DELETE -> variable.isPresent() && entity.isEmpty();
                case RETURN -> entity.isEmpty();
            };
            valid &= command == FlowCommand.CALL_LOGIC || invocation.isEmpty();
            valid &= command == FlowCommand.CALL_INTEGRATION || integrationInvocation.isEmpty();
            valid &= command == FlowCommand.IF || whenTrue.isEmpty() && whenFalse.isEmpty();
            if (!valid) {
                throw new IllegalArgumentException("invalid operands for flow command " + command);
            }
        }
    }

    public enum FlowCommand {
        VALIDATE_INPUT,
        FAIL,
        REQUIRE,
        CALL_LOGIC,
        CALL_INTEGRATION,
        FIND_BY,
        LIST_BY,
        SET_FIELD,
        ADD_TO,
        REMOVE_FROM,
        IF,
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
        LIST,
        SCALAR,
        VALUE
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
        PAGE,
        NOTHING
    }

    public record Failure(
            FailureCondition condition,
            Optional<String> field,
            Optional<String> name,
            int status,
            SourceRef where) {
        public Failure {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }

    /** {@code DOMAIN} carries the name the business gave the error; the rest are detected. */
    public enum FailureCondition {
        INVALID_INPUT,
        DUPLICATE,
        NOT_FOUND,
        DOMAIN
    }
}
