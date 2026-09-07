package dev.harpia.logic;

import dev.harpia.diag.SourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A fully typed statement of a Logic body. The set is closed and free of side effects. */
public sealed interface TypedStatement {

    SourceRef where();

    /** Single-assignment declaration of a local value. Harpia Logic has no reassignment. */
    record Assignment(String name, TypedExpression value, SourceRef where)
            implements TypedStatement {
        public Assignment {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }

        public LogicType type() {
            return value.type();
        }
    }

    record Conditional(
            TypedExpression condition,
            List<TypedStatement> thenBranch,
            Optional<List<TypedStatement>> elseBranch,
            SourceRef where) implements TypedStatement {
        public Conditional {
            Objects.requireNonNull(condition, "condition");
            thenBranch = List.copyOf(thenBranch);
            Objects.requireNonNull(elseBranch, "elseBranch");
            elseBranch = elseBranch.map(List::copyOf);
            Objects.requireNonNull(where, "where");
        }
    }

    record Return(TypedExpression value, SourceRef where) implements TypedStatement {
        public Return {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }
    }

    /** True when every path through {@code body} ends in a return. */
    static boolean definitelyReturns(List<TypedStatement> body) {
        Objects.requireNonNull(body, "body");
        for (TypedStatement statement : body) {
            if (statement instanceof Return) {
                return true;
            }
            if (statement instanceof Conditional conditional
                    && conditional.elseBranch().isPresent()
                    && definitelyReturns(conditional.thenBranch())
                    && definitelyReturns(conditional.elseBranch().orElseThrow())) {
                return true;
            }
        }
        return false;
    }
}
