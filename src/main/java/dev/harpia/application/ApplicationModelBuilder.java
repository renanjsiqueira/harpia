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
import dev.harpia.model.HttpBinding;
import dev.harpia.model.OperationNature;
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
                business.enums().stream()
                        .map(source -> new ApplicationEnum(
                                source.name(), source.values(), source.where()))
                        .toList(),
                business.values().stream()
                        .map(source -> new ApplicationValue(
                                source.name(),
                                source.components().stream()
                                        .map(ApplicationModelBuilder::field)
                                        .toList(),
                                source.where()))
                        .toList(),
                business.integrations().stream()
                        .map(source -> new ApplicationIntegration(
                                source.name(),
                                source.operations().stream()
                                        .map(operation -> new ApplicationIntegration.Operation(
                                                operation.name(),
                                                operation.input().stream()
                                                        .map(parameter ->
                                                                new ApplicationIntegration.Parameter(
                                                                        parameter.name(),
                                                                        fieldType(parameter.type()),
                                                                        parameter.required(),
                                                                        parameter.where()))
                                                        .toList(),
                                                new ApplicationIntegration.Result(
                                                        operation.output().type()
                                                                .map(ApplicationModelBuilder::fieldType),
                                                        operation.output().where()),
                                                operation.errors().stream()
                                                        .map(failure ->
                                                                new ApplicationIntegration.Failure(
                                                                        failure.name(),
                                                                        failure.where()))
                                                        .toList(),
                                                operation.where()))
                                        .toList(),
                                source.where()))
                        .toList(),
                business.events().stream()
                        .map(source -> new ApplicationEvent(
                                source.name(),
                                source.payload().stream()
                                        .map(field -> new ApplicationEvent.Field(
                                                field.name(),
                                                fieldType(field.type()),
                                                field.required(),
                                                field.where()))
                                        .toList(),
                                source.where()))
                        .toList(),
                business.entities().stream()
                        .map(source -> entity(source, identities(business)))
                        .toList(),
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
                source.customContract(),
                source.where());
    }

    /**
     * The scalar each entity is identified by.
     *
     * <p>A reference stores the identity of what it points at, so it has to be told what shape that
     * identity has. Only the whole project knows.
     */
    private static Map<String, ApplicationScalarType> identities(
            dev.harpia.model.ProjectModel business) {
        Map<String, ApplicationScalarType> identities = new LinkedHashMap<>();
        for (EntityModel entity : business.entities()) {
            entity.idField().type().scalarKind().ifPresent(scalar ->
                    identities.put(entity.name(), ApplicationScalarType.valueOf(scalar.name())));
        }
        return Map.copyOf(identities);
    }

    private static ApplicationEntity entity(
            EntityModel source, Map<String, ApplicationScalarType> identities) {
        List<ApplicationField> fields = source.fields().stream()
                .map(field -> field(field, identities))
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
                source.invariants().stream()
                        .map(invariant -> new ApplicationRule(
                                invariant.text(), invariant.condition(), invariant.where()))
                        .toList(),
                source.where());
    }

    private static ApplicationFieldType nominal(dev.harpia.model.FieldType type) {
        dev.harpia.model.FieldType.Nominal declared =
                (dev.harpia.model.FieldType.Nominal) type;
        return switch (declared.kind()) {
            case ENUM -> ApplicationFieldType.enumeration(declared.name(), declared.values());
            case VALUE -> ApplicationFieldType.value(
                    declared.name(),
                    declared.components().stream()
                            .map(ApplicationModelBuilder::field)
                            .toList());
        };
    }

    private static ApplicationFieldType fieldType(dev.harpia.model.FieldType type) {
        return fieldType(type, Map.of());
    }

    private static ApplicationFieldType fieldType(
            dev.harpia.model.FieldType type, Map<String, ApplicationScalarType> identities) {
        if (type instanceof dev.harpia.model.FieldType.Container container) {
            return ApplicationFieldType.list(fieldType(container.element(), identities));
        }
        if (type instanceof dev.harpia.model.FieldType.Optionality optional) {
            return ApplicationFieldType.optional(fieldType(optional.element(), identities));
        }
        if (type instanceof dev.harpia.model.FieldType.Reference reference) {
            return ApplicationFieldType.reference(
                    reference.entity(),
                    identities.getOrDefault(reference.entity(), ApplicationScalarType.UUID));
        }
        if (type instanceof dev.harpia.model.FieldType.Relationship relationship) {
            return ApplicationFieldType.relationship(
                    relationship.entity(),
                    identities.getOrDefault(relationship.entity(), ApplicationScalarType.UUID),
                    ApplicationFieldType.RelationshipLoading.valueOf(
                            relationship.loading().name()),
                    ApplicationFieldType.RelationshipLifecycle.valueOf(
                            relationship.lifecycle().name()));
        }
        return type.scalarKind()
                .<ApplicationFieldType>map(scalar ->
                        ApplicationFieldType.scalar(ApplicationScalarType.valueOf(scalar.name())))
                .orElseGet(() -> nominal(type));
    }

    private static ApplicationField field(FieldModel source) {
        return field(source, Map.of());
    }

    private static ApplicationField field(
            FieldModel source, Map<String, ApplicationScalarType> identities) {
        ApplicationFieldType type = fieldType(source.type(), identities);
        return new ApplicationField(
                source.name(),
                // A reference stores an identity and a direct relationship joins through one.
                type instanceof ApplicationFieldType.Reference
                                || type instanceof ApplicationFieldType.Relationship
                        ? SqlNaming.identifier(source.name()) + "_id"
                        : SqlNaming.identifier(source.name()),
                type,
                source.required(),
                source.unique(),
                source.generated(),
                source.indexed(),
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
        Kind kind = operationKind(source.flow().allSteps());
        return new ApplicationOperation(
                source.title(),
                lowerFirst(source.baseName()),
                source.nature(),
                kind,
                source.http().map(binding -> new Endpoint(
                        HttpMethod.valueOf(binding.method().name()),
                        binding.baseUrl(),
                        binding.path(),
                        binding.hasIdPathVariable(),
                        Access.valueOf(binding.access().name()),
                        binding.request().stream()
                                .map(ApplicationModelBuilder::requestMapping)
                                .toList(),
                        responseMapping(binding.response()),
                        binding.where(),
                        binding.endpointWhere())),
                input.isEmpty() ? Optional.empty() : Optional.of(source.baseName() + "Request"),
                input,
                source.rules().stream()
                        .map(rule -> new ApplicationRule(
                                rule.text(), rule.condition(), rule.where()))
                        .toList(),
                flow,
                variables(source.flow()),
                result(source.output(), entityName),
                source.errors().stream().map(ApplicationModelBuilder::failure).toList(),
                transactional(source.nature(), kind),
                source.where());
    }

    private static ApplicationOperation.RequestMapping requestMapping(
            HttpBinding.RequestMapping mapping) {
        return switch (mapping) {
            case HttpBinding.Path value -> new ApplicationOperation.Path(
                    value.input(), value.parameter(), value.where());
            case HttpBinding.Query value -> new ApplicationOperation.Query(
                    value.input(), value.parameter(), value.where());
            case HttpBinding.Header value -> new ApplicationOperation.Header(
                    value.input(), value.header(), value.where());
            case HttpBinding.Body value ->
                    new ApplicationOperation.Body(value.input(), value.where());
        };
    }

    private static ApplicationOperation.ResponseMapping responseMapping(
            HttpBinding.ResponseMapping mapping) {
        return switch (mapping) {
            case HttpBinding.ResponseBody value ->
                    new ApplicationOperation.ResponseBody(value.output(), value.where());
            case HttpBinding.NoResponse value ->
                    new ApplicationOperation.NoResponse(value.where());
        };
    }

    /**
     * A declared Command writes and a declared Query reads, whatever their steps happen to be.
     * Only a V0 heading, which declares nothing, still has its boundary inferred from the flow.
     */
    private static boolean transactional(OperationNature nature, Kind kind) {
        return switch (nature) {
            case COMMAND -> true;
            case QUERY -> false;
            case INFERRED -> kind == Kind.CREATE || kind == Kind.UPDATE || kind == Kind.DELETE;
        };
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

    private static java.util.List<FlowInstruction.SortOrder> sortOrders(
            java.util.List<FlowStep.SortOrder> source) {
        return source.stream()
                .map(order -> new FlowInstruction.SortOrder(order.field(), order.descending()))
                .toList();
    }

    private static FlowInstruction instruction(FlowStep source) {
        if (source instanceof FlowStep.ValidateInput value) {
            return new FlowInstruction(
                    FlowCommand.VALIDATE_INPUT, Optional.empty(), Optional.empty(), value.where());
        }
        if (source instanceof FlowStep.Fail value) {
            return new FlowInstruction(
                    FlowCommand.FAIL,
                    Optional.empty(),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    Optional.of(new FlowInstruction.TypedValue(
                            value.error(), value.text(), value.condition())),
                    value.where());
        }
        if (source instanceof FlowStep.Require value) {
            return new FlowInstruction(
                    FlowCommand.REQUIRE,
                    Optional.empty(),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    Optional.of(new FlowInstruction.TypedValue(
                            value.error(), value.text(), value.condition())),
                    value.where());
        }
        if (source instanceof FlowStep.Call value) {
            return new FlowInstruction(
                    FlowCommand.CALL_LOGIC,
                    Optional.of(value.variable()),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    Optional.empty(),
                    Optional.of(new FlowInstruction.Invocation(
                            value.logic(),
                            value.arguments().stream()
                                    .map(argument -> new FlowInstruction.Invocation.Argument(
                                            argument.name(),
                                            argument.value(),
                                            argument.parameterType()))
                                    .toList(),
                            value.resultType())),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    value.where());
        }
        if (source instanceof FlowStep.IntegrationCall value) {
            return new FlowInstruction(
                    FlowCommand.CALL_INTEGRATION,
                    value.variable(),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(new FlowInstruction.IntegrationInvocation(
                            value.integration(),
                            value.operation(),
                            value.arguments().stream()
                                    .map(argument ->
                                            new FlowInstruction.IntegrationInvocation.Argument(
                                                    argument.name(),
                                                    argument.value(),
                                                    argument.parameterType()))
                                    .toList(),
                            value.resultType())),
                    java.util.List.of(),
                    java.util.List.of(),
                    value.where());
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
        if (source instanceof FlowStep.FindBy value) {
            return new FlowInstruction(
                    FlowCommand.FIND_BY,
                    Optional.of(value.variable()),
                    Optional.of(value.entity()),
                    java.util.List.of(value.field()),
                    java.util.List.of(),
                    false,
                    Optional.empty(),
                    value.where());
        }
        if (source instanceof FlowStep.ListBy value) {
            return new FlowInstruction(
                    FlowCommand.LIST_BY,
                    Optional.of(value.variable()),
                    Optional.of(value.entity()),
                    value.fields(),
                    sortOrders(value.sort()),
                    value.paged(),
                    Optional.empty(),
                    value.where());
        }
        if (source instanceof FlowStep.Conditional value) {
            return new FlowInstruction(
                    FlowCommand.IF,
                    Optional.empty(),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    Optional.of(new FlowInstruction.TypedValue(
                            "if", value.text(), value.condition())),
                    Optional.empty(),
                    Optional.empty(),
                    value.whenTrue().stream()
                            .map(ApplicationModelBuilder::instruction)
                            .toList(),
                    value.whenFalse().stream()
                            .map(ApplicationModelBuilder::instruction)
                            .toList(),
                    value.where());
        }
        if (source instanceof FlowStep.ChangeCollection value) {
            return new FlowInstruction(
                    value.change() == dev.harpia.parse.SpecAst.CollectionChange.ADD
                            ? FlowCommand.ADD_TO
                            : FlowCommand.REMOVE_FROM,
                    Optional.of(value.variable()),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    Optional.of(new FlowInstruction.TypedValue(
                            value.field(), value.text(), value.element())),
                    value.where());
        }
        if (source instanceof FlowStep.SetField value) {
            return new FlowInstruction(
                    FlowCommand.SET_FIELD,
                    Optional.of(value.variable()),
                    Optional.empty(),
                    java.util.List.of(),
                    java.util.List.of(),
                    false,
                    Optional.of(new FlowInstruction.TypedValue(
                            value.field(), value.text(), value.value())),
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
                    java.util.List.of(),
                    sortOrders(value.sort()),
                    value.paged(),
                    Optional.empty(),
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
                        switch (type.kind()) {
                            case ENTITY -> VariableKind.ENTITY;
                            case LIST -> VariableKind.LIST;
                            case SCALAR -> VariableKind.SCALAR;
                            case VALUE -> VariableKind.VALUE;
                        },
                        type.entity())));
        return result;
    }

    private static Result result(OutputModel output, String entityName) {
        ResultKind kind = switch (output.shape().kind()) {
            case ENTITY -> ResultKind.ENTITY;
            case LIST -> ResultKind.LIST;
            case PAGE -> ResultKind.PAGE;
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
                    case DOMAIN -> FailureCondition.DOMAIN;
                },
                source.field(),
                source.name(),
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
