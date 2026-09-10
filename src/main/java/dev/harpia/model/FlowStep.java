package dev.harpia.model;

import dev.harpia.diag.SourceRef;
import java.util.Objects;
import java.util.Optional;

public sealed interface FlowStep
        permits FlowStep.ValidateInput,
                FlowStep.CreateFrom,
                FlowStep.LoadById,
                FlowStep.FindBy,
                FlowStep.UpdateFrom,
                FlowStep.ListAll,
                FlowStep.ListBy,
                FlowStep.Save,
                FlowStep.Delete,
                FlowStep.Fail,
                FlowStep.Return {

    SourceRef where();

    record ValidateInput(SourceRef where) implements FlowStep {
        public ValidateInput {
            Objects.requireNonNull(where, "where");
        }
    }

    /** Raises the named domain error when the typed condition holds. */
    record Fail(
            String error,
            String text,
            dev.harpia.logic.TypedExpression condition,
            SourceRef where) implements FlowStep {
        public Fail {
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(where, "where");
        }
    }

    record CreateFrom(String variable, String entity, SourceRef where) implements FlowStep {
        public CreateFrom {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(where, "where");
        }
    }

    record LoadById(String variable, String entity, SourceRef where) implements FlowStep {
        public LoadById {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(where, "where");
        }
    }

    /** Finds the single entity whose unique field holds the given input value. */
    record FindBy(String variable, String entity, String field, SourceRef where)
            implements FlowStep {
        public FindBy {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(where, "where");
        }
    }

    /** Lists every entity whose fields all match the given input values. */
    record ListBy(
            String variable,
            String entity,
            java.util.List<String> fields,
            java.util.List<SortOrder> sort,
            boolean paged,
            SourceRef where) implements FlowStep {
        public ListBy {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            fields = java.util.List.copyOf(fields);
            sort = java.util.List.copyOf(sort);
            Objects.requireNonNull(where, "where");
        }
    }

    /** One ordering step: a field and whether it descends. */
    record SortOrder(String field, boolean descending) {
        public SortOrder {
            Objects.requireNonNull(field, "field");
        }
    }

    record UpdateFrom(String variable, SourceRef where) implements FlowStep {
        public UpdateFrom {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(where, "where");
        }
    }

    record ListAll(
            String variable,
            String entity,
            java.util.List<SortOrder> sort,
            boolean paged,
            SourceRef where) implements FlowStep {
        public ListAll {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(entity, "entity");
            sort = java.util.List.copyOf(sort);
            Objects.requireNonNull(where, "where");
        }
    }

    record Save(String variable, SourceRef where) implements FlowStep {
        public Save {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(where, "where");
        }
    }

    record Delete(String variable, SourceRef where) implements FlowStep {
        public Delete {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(where, "where");
        }
    }

    record Return(Optional<String> variable, SourceRef where) implements FlowStep {
        public Return {
            Objects.requireNonNull(variable, "variable");
            Objects.requireNonNull(where, "where");
        }
    }
}
