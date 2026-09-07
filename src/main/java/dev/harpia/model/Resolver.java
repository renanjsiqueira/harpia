package dev.harpia.model;

import dev.harpia.parse.SpecAst;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts a semantically valid syntax tree into the complete emitter-facing model. */
public final class Resolver {

    private Resolver() {
    }

    public static ProjectModel resolve(
            List<SpecAst> specifications,
            List<LogicModel> logics,
            List<ScenarioModel> scenarios) {
        Objects.requireNonNull(specifications, "specifications");
        Objects.requireNonNull(logics, "logics");
        Objects.requireNonNull(scenarios, "scenarios");
        return new ProjectModel(
                specifications.stream()
                        .filter(SpecAst::declaresEntity)
                        .map(Resolver::entity)
                        .toList(),
                logics,
                scenarios);
    }

    private static EntityModel entity(SpecAst specification) {
        List<FieldModel> fields = specification.fields().stream()
                .map(Resolver::field)
                .toList();
        FieldModel idField = fields.stream()
                .filter(field -> field.name().equals("id")
                        && field.type() == TypeRef.UUID
                        && field.generated())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resolver requires semantic validation before resolving "
                                + specification.entityName()));
        List<UseCaseModel> useCases = specification.useCases().stream()
                .map(useCase -> useCase(useCase, fields))
                .toList();
        return new EntityModel(
                specification.entityName(),
                fields,
                idField,
                useCases,
                specification.where());
    }

    private static FieldModel field(SpecAst.FieldDeclaration field) {
        return new FieldModel(
                field.name(),
                TypeRef.fromSyntax(field.type()),
                field.required(),
                field.unique(),
                field.generated(),
                field.defaultValue().map(Literal::new),
                field.where());
    }

    private static UseCaseModel useCase(
            SpecAst.UseCaseDeclaration useCase, List<FieldModel> entityFields) {
        Map<String, FieldModel> fieldsByName = new LinkedHashMap<>();
        entityFields.forEach(field -> fieldsByName.putIfAbsent(field.name(), field));

        List<FieldModel> input = new ArrayList<>();
        for (SpecAst.InputDeclaration declaration : useCase.input()) {
            FieldModel entityField = fieldsByName.get(declaration.name());
            input.add(new FieldModel(
                    declaration.name(),
                    TypeRef.fromSyntax(declaration.type()),
                    declaration.required(),
                    entityField.unique(),
                    false,
                    entityField.defaultValue(),
                    declaration.where()));
        }

        LinkedHashMap<String, FlowModel.ValueType> variables = new LinkedHashMap<>();
        List<FlowStep> steps = useCase.flow().stream()
                .map(statement -> flowStep(statement, variables))
                .toList();

        OutputModel.Shape shape = new OutputModel.Shape(
                switch (useCase.output().shape().kind()) {
                    case ENTITY -> OutputModel.Kind.ENTITY;
                    case LIST -> OutputModel.Kind.LIST;
                    case NOTHING -> OutputModel.Kind.NOTHING;
                },
                useCase.output().shape().entity());

        List<ErrorMapping> errors = useCase.errors().stream()
                .map(error -> new ErrorMapping(
                        switch (error.kind()) {
                            case INVALID_INPUT -> ErrorMapping.Condition.INVALID_INPUT;
                            case DUPLICATE -> ErrorMapping.Condition.DUPLICATE;
                            case NOT_FOUND -> ErrorMapping.Condition.NOT_FOUND;
                        },
                        error.field(),
                        error.status(),
                        error.where()))
                .toList();

        return new UseCaseModel(
                useCase.title(),
                Naming.useCaseBaseName(useCase.title()),
                new HttpBinding(
                        HttpBinding.HttpMethod.valueOf(useCase.endpoint().method()),
                        useCase.endpoint().path(),
                        useCase.endpoint().path().endsWith("/{id}")),
                AccessRule.PUBLIC,
                input,
                new FlowModel(steps, variables),
                new OutputModel(useCase.output().status(), shape),
                errors,
                useCase.where());
    }

    private static FlowStep flowStep(
            SpecAst.FlowStatement statement,
            LinkedHashMap<String, FlowModel.ValueType> variables) {
        if (statement instanceof SpecAst.ValidateInput value) {
            return new FlowStep.ValidateInput(value.where());
        }
        if (statement instanceof SpecAst.CreateFrom value) {
            variables.put(value.variable(), new FlowModel.ValueType(
                    FlowModel.Kind.ENTITY, value.entity()));
            return new FlowStep.CreateFrom(value.variable(), value.entity(), value.where());
        }
        if (statement instanceof SpecAst.LoadById value) {
            variables.put(value.variable(), new FlowModel.ValueType(
                    FlowModel.Kind.ENTITY, value.entity()));
            return new FlowStep.LoadById(value.variable(), value.entity(), value.where());
        }
        if (statement instanceof SpecAst.UpdateFrom value) {
            return new FlowStep.UpdateFrom(value.variable(), value.where());
        }
        if (statement instanceof SpecAst.ListAll value) {
            variables.put(value.variable(), new FlowModel.ValueType(
                    FlowModel.Kind.LIST, value.entity()));
            return new FlowStep.ListAll(value.variable(), value.entity(), value.where());
        }
        if (statement instanceof SpecAst.Save value) {
            return new FlowStep.Save(value.variable(), value.where());
        }
        if (statement instanceof SpecAst.Delete value) {
            return new FlowStep.Delete(value.variable(), value.where());
        }
        if (statement instanceof SpecAst.Return value) {
            return new FlowStep.Return(value.variable(), value.where());
        }
        throw new IllegalArgumentException("unknown flow statement " + statement.getClass().getName());
    }
}
