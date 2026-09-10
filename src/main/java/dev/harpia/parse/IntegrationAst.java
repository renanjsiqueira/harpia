package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
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

    /** One callable operation of an integration; its contract is added by INTEG-002. */
    public record Operation(String name, SourceRef where) {
        public Operation {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(where, "where");
        }
    }
}
