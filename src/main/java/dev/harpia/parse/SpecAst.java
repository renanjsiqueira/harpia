package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lossless-enough syntax tree produced before names and flow variables are resolved.
 *
 * <p>A module declares zero or one entity. A module without {@code ## Data} declares no entity and
 * carries only project-level declarations such as Logic.
 */
public record SpecAst(
        String file,
        String moduleName,
        boolean declaresEntity,
        List<FieldDeclaration> fields,
        List<UseCaseDeclaration> useCases,
        List<LogicAst.Declaration> logics,
        List<LogicAst.Scenario> scenarios,
        SourceRef where) {

    public SpecAst {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(moduleName, "moduleName");
        fields = List.copyOf(fields);
        useCases = List.copyOf(useCases);
        logics = List.copyOf(logics);
        scenarios = List.copyOf(scenarios);
        Objects.requireNonNull(where, "where");
        if (!declaresEntity && !fields.isEmpty()) {
            throw new IllegalArgumentException("only a module with '## Data' declares fields");
        }
    }

    /** The entity name of a module that declares {@code ## Data}. */
    public String entityName() {
        if (!declaresEntity) {
            throw new IllegalStateException("module " + moduleName + " declares no entity");
        }
        return moduleName;
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

    public enum ErrorKind {
        INVALID_INPUT,
        DUPLICATE,
        NOT_FOUND
    }

    public record ErrorDeclaration(
            ErrorKind kind, Optional<String> field, int status, SourceRef where) {
        public ErrorDeclaration {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(where, "where");
            if ((kind == ErrorKind.DUPLICATE) != field.isPresent()) {
                throw new IllegalArgumentException("only duplicate errors name a field");
            }
        }
    }

    public record UseCaseDeclaration(
            String title,
            Endpoint endpoint,
            Access access,
            List<InputDeclaration> input,
            List<FlowStatement> flow,
            Output output,
            List<ErrorDeclaration> errors,
            SourceRef where) {
        public UseCaseDeclaration {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(access, "access");
            input = List.copyOf(input);
            flow = List.copyOf(flow);
            Objects.requireNonNull(output, "output");
            errors = List.copyOf(errors);
            Objects.requireNonNull(where, "where");
        }
    }
}
