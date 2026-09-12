package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.InputDeclaration;
import java.util.List;
import java.util.Objects;

/** Syntax nodes for an outbound integration and the operations it exposes as ports. */
public final class IntegrationAst {

    private IntegrationAst() {
    }

    public record Declaration(String name, List<Operation> operations, SourceRef where)
            implements DeclarationAst {
        public Declaration {
            Objects.requireNonNull(name, "name");
            operations = List.copyOf(operations);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.INTEGRATION;
        }

        @Override
        public String declaredName() {
            return name;
        }
    }

    /** One callable operation and its transport-independent value contract. */
    public record Operation(
            String name,
            List<InputDeclaration> input,
            Output output,
            List<Failure> errors,
            SourceRef where) {
        public Operation {
            Objects.requireNonNull(name, "name");
            input = List.copyOf(input);
            Objects.requireNonNull(output, "output");
            errors = List.copyOf(errors);
            Objects.requireNonNull(where, "where");
        }
    }

    /** The successful value returned by a port; {@code nothing} represents no returned value. */
    public record Output(String type, SourceRef where) {
        public Output {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(where, "where");
        }

        public boolean returnsNothing() {
            return type.equals("nothing");
        }
    }

    /** A named failure variant exposed by the port contract. */
    public record Failure(String name, SourceRef where) {
        public Failure {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }
}
