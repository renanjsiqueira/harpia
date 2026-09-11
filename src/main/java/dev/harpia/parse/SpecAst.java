package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * V0 declaration nodes produced before names and flow variables are resolved.
 *
 * <p>The project and source-module containers are {@link ProjectAst} and {@link ModuleAst}. Keeping
 * the small V0 nodes here avoids coupling the new project tree to a particular future declaration
 * layout.
 */
public final class SpecAst {

    private SpecAst() {
    }

    /** The implicit V0 entity introduced by one module's {@code ## Data} section. */
    public record EntityDeclaration(
            String name, List<FieldDeclaration> fields, SourceRef where)
            implements DeclarationAst {
        public EntityDeclaration {
            Objects.requireNonNull(name, "name");
            fields = List.copyOf(fields);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.ENTITY;
        }

        @Override
        public String declaredName() {
            return name;
        }
    }

    /** One value of a declared enum, in the specification's own vocabulary. */
    public record EnumValue(String name, SourceRef where) {
        public EnumValue {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }

    /** A closed set of values the project names itself. */
    public record EnumDeclaration(String name, List<EnumValue> values, SourceRef where)
            implements DeclarationAst {
        public EnumDeclaration {
            Objects.requireNonNull(name, "name");
            values = List.copyOf(values);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.ENUM;
        }

        @Override
        public String declaredName() {
            return name;
        }
    }

    /** A named group of fields compared by what it holds, not by an identity. */
    public record ValueDeclaration(
            String name, List<FieldDeclaration> fields, SourceRef where)
            implements DeclarationAst {
        public ValueDeclaration {
            Objects.requireNonNull(name, "name");
            fields = List.copyOf(fields);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.VALUE;
        }

        @Override
        public String declaredName() {
            return name;
        }
    }

    public record FieldDeclaration(
            String name,
            String type,
            boolean required,
            boolean unique,
            boolean generated,
            boolean owned,
            boolean indexed,
            Optional<String> defaultValue,
            SourceRef where) {
        public FieldDeclaration {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(where, "where");
        }
    }

    public record InputDeclaration(String name, String type, boolean required, SourceRef where) {
        public InputDeclaration {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
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
            ROLE
        }

        public static final Access PUBLIC = new Access(Kind.PUBLIC, java.util.List.of());
        public static final Access AUTHENTICATED =
                new Access(Kind.AUTHENTICATED, java.util.List.of());

        public Access {
            java.util.Objects.requireNonNull(kind, "kind");
            roles = java.util.List.copyOf(roles);
            if (roles.isEmpty() == (kind == Kind.ROLE)) {
                throw new IllegalArgumentException("ROLE names roles and nothing else does");
            }
        }

        public static Access role(java.util.List<String> roles) {
            return new Access(Kind.ROLE, roles);
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
            return kind == Kind.ROLE ? "ROLE " + String.join(" or ", roles) : kind.name();
        }
    }

    public sealed interface FlowStatement
            permits ValidateInput, CreateFrom, LoadById, FindBy, UpdateFrom, SetField,
                    ChangeCollection, Conditional, ListAll, ListBy, Save, Delete, Fail, Require,
                    Call, Return {
        SourceRef where();
    }

    public record ValidateInput(SourceRef where) implements FlowStatement {}

    /**
     * Raises a declared domain error when a condition holds.
     *
     * <p>The guard is not decoration: a {@code fail} that always fired would end every run of the
     * operation, so the condition is what makes it an instruction rather than a dead end.
     */
    public record Fail(
            String error, String text, LogicAst.Expression condition, SourceRef where)
            implements FlowStatement {
        public Fail {
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(where, "where");
        }
    }

    /** Continues only when its boolean precondition holds; otherwise raises a declared error. */
    public record Require(
            String text, LogicAst.Expression condition, String error, SourceRef where)
            implements FlowStatement {
        public Require {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(where, "where");
        }
    }

    /**
     * Invokes a named application boundary. A dotted target is an Integration operation; a simple
     * target is resolved against Logic and Command declarations in the semantic pass.
     */
    public record Call(
            Optional<String> variable,
            String target,
            Optional<String> operation,
            List<LogicAst.NamedArgument> arguments,
            SourceRef where) implements FlowStatement {
        public Call {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(operation, "operation");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(where, "where");
        }

        public String displayTarget() {
            return target + operation.map(name -> "." + name).orElse("");
        }
    }

    public record CreateFrom(String variable, String entity, SourceRef where) implements FlowStatement {}

    public record LoadById(String variable, String entity, SourceRef where) implements FlowStatement {}

    /** Finds the single entity whose unique field holds the given input value. */
    public record FindBy(String variable, String entity, String field, SourceRef where)
            implements FlowStatement {
        public FindBy {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(where, "where");
        }
    }

    public record UpdateFrom(String variable, SourceRef where) implements FlowStatement {}

    /**
     * Two branches, one of which runs.
     *
     * <p>A flow that says {@code fail ... when} answers a question by stopping. A conditional
     * answers it by doing something different, which is why both exist.
     */
    public record Conditional(
            String text,
            LogicAst.Expression condition,
            List<FlowStatement> whenTrue,
            List<FlowStatement> whenFalse,
            SourceRef where) implements FlowStatement {
        public Conditional {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(condition, "condition");
            whenTrue = List.copyOf(whenTrue);
            whenFalse = List.copyOf(whenFalse);
            Objects.requireNonNull(where, "where");
            if (whenTrue.isEmpty()) {
                throw new IllegalArgumentException("conditional true branch must not be empty");
            }
        }
    }

    /** Whether an element joins a collection or leaves it. */
    public enum CollectionChange {
        ADD,
        REMOVE
    }

    /**
     * Adds an element to a collection field, or takes one out.
     *
     * <p>A collection is changed in place rather than replaced: {@code set} assigns a whole value,
     * and saying "one more" is not the same as saying "these".
     */
    public record ChangeCollection(
            CollectionChange change,
            String variable,
            String field,
            String text,
            LogicAst.Expression element,
            SourceRef where) implements FlowStatement {
        public ChangeCollection {
            Objects.requireNonNull(change, "change");
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(where, "where");
        }
    }

    /**
     * Assigns one field of an entity from an expression.
     *
     * <p>{@code update ... from input} copies whatever the input carries. This computes a single
     * value, which is the reason it exists: the two are different operations, and saying which one
     * happened is the point.
     */
    public record SetField(
            String variable,
            String field,
            String text,
            LogicAst.Expression value,
            SourceRef where) implements FlowStatement {
        public SetField {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }
    }

    /** One ordering step: a field and whether it descends. */
    public record SortOrder(String field, boolean descending) {
        public SortOrder {
            Objects.requireNonNull(field, "field");
        }
    }

    public record ListAll(
            String variable,
            String entity,
            List<SortOrder> sort,
            boolean paged,
            SourceRef where) implements FlowStatement {
        public ListAll {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            sort = List.copyOf(sort);
            Objects.requireNonNull(where, "where");
        }
    }

    /** Lists every entity whose fields all match the given input values. */
    public record ListBy(
            String variable,
            String entity,
            List<String> fields,
            List<SortOrder> sort,
            boolean paged,
            SourceRef where)
            implements FlowStatement {
        public ListBy {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            fields = List.copyOf(fields);
            sort = List.copyOf(sort);
            Objects.requireNonNull(where, "where");
        }
    }

    public record Save(String variable, SourceRef where) implements FlowStatement {}

    public record Delete(String variable, SourceRef where) implements FlowStatement {}

    /** {@code variable} is empty for {@code return nothing}. */
    public record Return(Optional<String> variable, SourceRef where) implements FlowStatement {
        public Return {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(where, "where");
        }
    }

    public enum OutputKind {
        ENTITY,
        LIST,
        PAGE,
        NOTHING
    }

    public record OutputShape(OutputKind kind, Optional<String> entity) {
        public OutputShape {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(entity, "entity");
            if ((kind == OutputKind.NOTHING) == entity.isPresent()) {
                throw new IllegalArgumentException("only entity and list output shapes name an entity");
            }
        }
    }

    public record Output(int status, OutputShape shape, SourceRef where) {
        public Output {
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(where, "where");
        }
    }

    /**
     * {@code DOMAIN} is an error the business names itself, such as {@code insufficient balance}.
     * The other three are conditions the runtime detects, so the compiler knows when they occur; a
     * domain error is declared as part of the contract and is raised by the code, not inferred.
     */
    public enum ErrorKind {
        INVALID_INPUT,
        DUPLICATE,
        NOT_FOUND,
        DOMAIN
    }

    public record ErrorDeclaration(
            ErrorKind kind,
            Optional<String> field,
            Optional<String> name,
            int status,
            SourceRef where) {
        public ErrorDeclaration {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
            if ((kind == ErrorKind.DUPLICATE) != field.isPresent()) {
                throw new IllegalArgumentException("only duplicate errors name a field");
            }
            if ((kind == ErrorKind.DOMAIN) != name.isPresent()) {
                throw new IllegalArgumentException("only domain errors carry a name");
            }
        }
    }

    /**
     * A condition the operation's input must satisfy, written as a Harpia expression.
     *
     * <p>{@code text} is kept beside the parsed expression because a violated rule reports itself,
     * and the sentence the specification wrote is what a reader recognises.
     */
    public record RuleDeclaration(
            String text, LogicAst.Expression condition, SourceRef where) {
        public RuleDeclaration {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(where, "where");
        }
    }

    /** Conditions the module's entity satisfies whenever it is stored. */
    public record InvariantDeclaration(
            String entity, List<RuleDeclaration> conditions, SourceRef where)
            implements DeclarationAst {
        public InvariantDeclaration {
            Objects.requireNonNull(entity, "entity");
            conditions = List.copyOf(conditions);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.INVARIANT;
        }

        @Override
        public String declaredName() {
            return entity;
        }
    }

    public record UseCaseDeclaration(
            DeclarationKind declaredKind,
            String title,
            Optional<Endpoint> endpoint,
            Optional<Access> access,
            List<InputDeclaration> input,
            List<RuleDeclaration> rules,
            List<FlowStatement> flow,
            Output output,
            List<ErrorDeclaration> errors,
            SourceRef where) implements DeclarationAst {
        public UseCaseDeclaration {
            Objects.requireNonNull(declaredKind, "declaredKind");
            if (!declaredKind.isOperation()) {
                throw new IllegalArgumentException(
                        "an operation cannot be declared as " + declaredKind);
            }
            Objects.requireNonNull(title, "title");
            rules = List.copyOf(rules);
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(access, "access");
            if (endpoint.isPresent() != access.isPresent()) {
                throw new IllegalArgumentException(
                        "endpoint and access must either both be present or both be absent");
            }
            input = List.copyOf(input);
            flow = List.copyOf(flow);
            Objects.requireNonNull(output, "output");
            errors = List.copyOf(errors);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return declaredKind;
        }

        @Override
        public String declaredName() {
            return title;
        }
    }
}
