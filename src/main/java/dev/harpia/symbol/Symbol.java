package dev.harpia.symbol;

import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.model.LogicModel;
import java.util.List;
import java.util.Objects;

/**
 * One name the project declares, with everything a reference needs to be checked against it.
 *
 * <p>A symbol carries the module it came from, so a diagnostic can say where a name is defined and
 * so a cross-module reference can be recognised as such instead of merely failing to resolve.
 */
public sealed interface Symbol {

    String name();

    Namespace namespace();

    /** The file that declares it. */
    String module();

    SourceRef where();

    record Entity(String name, String module, SourceRef where) implements Symbol {
        public Entity {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(where, "where");
        }

        @Override
        public Namespace namespace() {
            return Namespace.TYPES;
        }
    }

    /** A declared enum and its values, in declaration order. */
    record EnumType(String name, String module, java.util.List<String> values, SourceRef where)
            implements Symbol {
        public EnumType {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(module, "module");
            values = java.util.List.copyOf(values);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public Namespace namespace() {
            return Namespace.TYPES;
        }
    }

    /** A declared value and the fields it groups, in declaration order. */
    record ValueType(
            String name,
            String module,
            java.util.List<dev.harpia.parse.SpecAst.FieldDeclaration> fields,
            SourceRef where) implements Symbol {
        public ValueType {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(module, "module");
            fields = java.util.List.copyOf(fields);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public Namespace namespace() {
            return Namespace.TYPES;
        }
    }

    record Operation(String name, String module, String title, SourceRef where) implements Symbol {
        public Operation {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(where, "where");
        }

        @Override
        public Namespace namespace() {
            return Namespace.OPERATIONS;
        }
    }

    /** An outbound system boundary and the operations callers may invoke on it. */
    record Integration(
            String name,
            String module,
            List<dev.harpia.parse.IntegrationAst.Operation> operations,
            SourceRef where) implements Symbol {
        public Integration {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(module, "module");
            operations = List.copyOf(operations);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public Namespace namespace() {
            return Namespace.INTEGRATIONS;
        }

        public java.util.Optional<dev.harpia.parse.IntegrationAst.Operation> operation(
                String operationName) {
            return operations.stream()
                    .filter(operation -> operation.name().equals(operationName))
                    .findFirst();
        }
    }

    /** A pure computation, with the signature every call and scenario is checked against. */
    record Computation(
            String name,
            String module,
            List<LogicModel.Parameter> parameters,
            LogicType returnType,
            SourceRef where) implements Symbol {

        public Computation {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(module, "module");
            parameters = List.copyOf(parameters);
            Objects.requireNonNull(returnType, "returnType");
            Objects.requireNonNull(where, "where");
        }

        @Override
        public Namespace namespace() {
            return Namespace.COMPUTATIONS;
        }

        public java.util.Optional<LogicModel.Parameter> parameter(String parameterName) {
            return parameters.stream()
                    .filter(parameter -> parameter.name().equals(parameterName))
                    .findFirst();
        }
    }

    record Scenario(String name, String module, String computation, SourceRef where)
            implements Symbol {
        public Scenario {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(computation, "computation");
            Objects.requireNonNull(where, "where");
        }

        @Override
        public Namespace namespace() {
            return Namespace.TESTS;
        }
    }
}
