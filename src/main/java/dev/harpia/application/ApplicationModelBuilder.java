package dev.harpia.application;

import dev.harpia.application.ApplicationOperation.Access;
import dev.harpia.application.ApplicationOperation.Endpoint;
import dev.harpia.application.ApplicationOperation.Failure;
import dev.harpia.application.ApplicationOperation.FailureCondition;
import dev.harpia.application.ApplicationOperation.FlowCommand;
import dev.harpia.application.ApplicationOperation.FlowInstruction;
import dev.harpia.application.ApplicationOperation.HttpMethod;
import dev.harpia.application.ApplicationOperation.Kind;
import dev.harpia.application.ApplicationOperation.Result;
import dev.harpia.application.ApplicationOperation.ResultKind;
import dev.harpia.application.ApplicationOperation.VariableKind;
import dev.harpia.application.ApplicationOperation.VariableType;
import dev.harpia.capability.ResolvedCapabilities;
import dev.harpia.model.EntityModel;
import dev.harpia.model.ErrorMapping;
import dev.harpia.model.FieldModel;
import dev.harpia.model.FlowModel;
import dev.harpia.model.FlowStep;
import dev.harpia.model.LogicModel;
import dev.harpia.model.ScenarioModel;
import dev.harpia.model.OutputModel;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.UseCaseModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Lowers the framework-free Business IR into the closed, generator-facing Application IR. */
public final class ApplicationModelBuilder {

    private ApplicationModelBuilder() {
    }

    public static ApplicationProject build(
            ProjectSettings settings,
            ProjectModel business,
            ResolvedCapabilities capabilities) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(business, "business");
        Objects.requireNonNull(capabilities, "capabilities");
        return new ApplicationProject(
                settings,
                business.entities().stream().map(ApplicationModelBuilder::entity).toList(),
                business.logics().stream().map(ApplicationModelBuilder::logic).toList(),
                business.scenarios().stream().map(ApplicationModelBuilder::scenario).toList(),
                capabilities);
    }

    private static ApplicationScenario scenario(ScenarioModel source) {
        return new ApplicationScenario(
                source.title(),
                source.methodName(),
                source.computation(),
                source.arguments().stream()
                        .map(argument -> new ApplicationScenario.Argument(
                                argument.name(), argument.type(), argument.value().source()))
                        .toList(),
                source.resultType(),
                source.expected().source(),
                source.where());
    }

    private static ApplicationLogic logic(LogicModel source) {
        return new ApplicationLogic(
                source.name(),
                source.parameters().stream()
                        .map(parameter -> new ApplicationLogic.Parameter(
                                parameter.name(), parameter.type()))
                        .toList(),
                source.returnType(),
                source.body(),
                source.where());
    }

    private static ApplicationEntity entity(EntityModel source) {
        List<ApplicationField> fields = source.fields().stream()
                .map(ApplicationModelBuilder::field)
                .toList();
        Map<String, ApplicationField> fieldsByName = new LinkedHashMap<>();
        fields.forEach(field -> fieldsByName.put(field.name(), field));
        String entityName = source.name();
        return new ApplicationEntity(
                entityName,
                SqlNaming.identifier(entityName),
                entityName + "Response",
                fields,
                fieldsByName.get(source.idField().name()),
                source.useCases().stream()
                        .map(useCase -> operation(useCase, entityName))
                        .toList(),
                source.where());
    }

    private static ApplicationField field(FieldModel source) {
        ApplicationScalarType type = ApplicationScalarType.valueOf(source.type().name());
        return new ApplicationField(
                source.name(),
                SqlNaming.identifier(source.name()),
                type,
                source.required(),
                source.unique(),
                source.generated(),
                source.defaultValue().map(value -> value.source()),
                source.where());
    }

    private static ApplicationOperation operation(UseCaseModel source, String entityName) {
        List<ApplicationField> input = source.input().stream()
                .map(ApplicationModelBuilder::field)
                .toList();
        List<FlowInstruction> flow = source.flow().steps().stream()
                .map(ApplicationModelBuilder::instruction)
                .toList();
        Kind kind = operationKind(source.flow().steps());
        return new ApplicationOperation(
                source.title(),
                lowerFirst(source.baseName()),
                kind,
                new Endpoint(
                        HttpMethod.valueOf(source.http().method().name()),
                        source.http().path(),
                        source.http().hasIdPathVariable(),
                        Access.PUBLIC),
                input.isEmpty() ? Optional.empty() : Optional.of(source.baseName() + "Request"),
                input,
                flow,
                variables(source.flow()),
                result(source.output(), entityName),
                source.errors().stream().map(ApplicationModelBuilder::failure).toList(),
                kind == Kind.CREATE || kind == Kind.UPDATE || kind == Kind.DELETE,
                source.where());
    }

    private static Kind operationKind(List<FlowStep> steps) {
        if (steps.stream().anyMatch(FlowStep.Delete.class::isInstance)) {
            return Kind.DELETE;
        }
        if (steps.stream().anyMatch(FlowStep.UpdateFrom.class::isInstance)) {
            return Kind.UPDATE;
        }
        if (steps.stream().anyMatch(FlowStep.CreateFrom.class::isInstance)) {
            return Kind.CREATE;
        }
        if (steps.stream().anyMatch(FlowStep.ListAll.class::isInstance)) {
            return Kind.LIST;
        }
        return Kind.READ;
    }

    private static FlowInstruction instruction(FlowStep source) {
        if (source instanceof FlowStep.ValidateInput value) {
            return new FlowInstruction(
                    FlowCommand.VALIDATE_INPUT, Optional.empty(), Optional.empty(), value.where());
        }
        if (source instanceof FlowStep.CreateFrom value) {
            return new FlowInstruction(
                    FlowCommand.CREATE_FROM,
                    Optional.of(value.variable()),
                    Optional.of(value.entity()),
                    value.where());
        }
        if (source instanceof FlowStep.LoadById value) {
            return new FlowInstruction(
                    FlowCommand.LOAD_BY_ID,
                    Optional.of(value.variable()),
                    Optional.of(value.entity()),
                    value.where());
        }
        if (source instanceof FlowStep.UpdateFrom value) {
            return new FlowInstruction(
                    FlowCommand.UPDATE_FROM,
                    Optional.of(value.variable()),
                    Optional.empty(),
                    value.where());
        }
        if (source instanceof FlowStep.ListAll value) {
            return new FlowInstruction(
                    FlowCommand.LIST_ALL,
                    Optional.of(value.variable()),
                    Optional.of(value.entity()),
                    value.where());
        }
        if (source instanceof FlowStep.Save value) {
            return new FlowInstruction(
                    FlowCommand.SAVE,
                    Optional.of(value.variable()),
                    Optional.empty(),
                    value.where());
        }
        if (source instanceof FlowStep.Delete value) {
            return new FlowInstruction(
                    FlowCommand.DELETE,
                    Optional.of(value.variable()),
                    Optional.empty(),
                    value.where());
        }
        if (source instanceof FlowStep.Return value) {
            return new FlowInstruction(
                    FlowCommand.RETURN, value.variable(), Optional.empty(), value.where());
        }
        throw new IllegalArgumentException("unknown flow step " + source.getClass().getName());
    }

    private static Map<String, VariableType> variables(FlowModel flow) {
        LinkedHashMap<String, VariableType> result = new LinkedHashMap<>();
        flow.variables().forEach((name, type) -> result.put(
                name,
                new VariableType(
                        type.kind() == FlowModel.Kind.ENTITY
                                ? VariableKind.ENTITY
                                : VariableKind.LIST,
                        type.entity())));
        return result;
    }

    private static Result result(OutputModel output, String entityName) {
        ResultKind kind = switch (output.shape().kind()) {
            case ENTITY -> ResultKind.ENTITY;
            case LIST -> ResultKind.LIST;
            case NOTHING -> ResultKind.NOTHING;
        };
        return new Result(
                output.status(),
                kind,
                kind == ResultKind.NOTHING
                        ? Optional.empty()
                        : Optional.of(entityName + "Response"));
    }

    private static Failure failure(ErrorMapping source) {
        return new Failure(
                switch (source.condition()) {
                    case INVALID_INPUT -> FailureCondition.INVALID_INPUT;
                    case DUPLICATE -> FailureCondition.DUPLICATE;
                    case NOT_FOUND -> FailureCondition.NOT_FOUND;
                },
                source.field(),
                source.status(),
                source.where());
    }

    private static String lowerFirst(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("operation base name must not be empty");
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
