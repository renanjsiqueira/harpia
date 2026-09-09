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

    public enum Access {
        PUBLIC
    }

    public sealed interface FlowStatement
            permits ValidateInput, CreateFrom, LoadById, UpdateFrom, ListAll, Save, Delete, Return {
        SourceRef where();
    }

    public record ValidateInput(SourceRef where) implements FlowStatement {}

    public record CreateFrom(String variable, String entity, SourceRef where) implements FlowStatement {}

    public record LoadById(String variable, String entity, SourceRef where) implements FlowStatement {}

    public record UpdateFrom(String variable, SourceRef where) implements FlowStatement {}

    public record ListAll(String variable, String entity, SourceRef where) implements FlowStatement {}

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
