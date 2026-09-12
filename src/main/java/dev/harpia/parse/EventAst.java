package dev.harpia.parse;

import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.InputDeclaration;
import java.util.List;
import java.util.Objects;

/** Syntax nodes for a domain event and the payload it announces. */
public final class EventAst {

    private EventAst() {
    }

    /**
     * Something that happened, named in the past and announced to whoever cares.
     *
     * <p>An event has no caller and no result: nothing waits for it, so it declares only what it
     * carries. That is what separates it from an Integration operation, which is a call someone
     * makes and whose answer someone reads.
     */
    public record Declaration(String name, List<InputDeclaration> payload, SourceRef where)
            implements DeclarationAst {
        public Declaration {
            Objects.requireNonNull(name, "name");
            payload = List.copyOf(payload);
            Objects.requireNonNull(where, "where");
        }

        @Override
        public DeclarationKind kind() {
            return DeclarationKind.EVENT;
        }

        @Override
        public String declaredName() {
            return name;
        }
    }
}
