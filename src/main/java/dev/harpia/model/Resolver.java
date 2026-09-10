package dev.harpia.model;

import dev.harpia.binding.BindingModel;
import dev.harpia.parse.FieldLineParser;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecAst;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/** Converts a semantically valid syntax tree into the complete emitter-facing model. */
public final class Resolver {

    private Resolver() {
    }

    public static ProjectModel resolve(
            ProjectAst syntax,
            BindingModel bindings,
            List<LogicModel> logics,
            List<ScenarioModel> scenarios,
            Map<String, List<RuleModel>> rules,
            Map<String, List<RuleModel>> invariants,
            Map<dev.harpia.diag.SourceRef, dev.harpia.logic.TypedExpression> guards) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(logics, "logics");
        Objects.requireNonNull(scenarios, "scenarios");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(invariants, "invariants");
        Map<String, List<String>> enums = new LinkedHashMap<>();
        Map<String, List<FieldModel>> values = new LinkedHashMap<>();
        for (ModuleAst module : syntax.modules()) {
            for (SpecAst.EnumDeclaration source : module.enums()) {
                enums.put(
                        source.name(),
                        source.values().stream().map(SpecAst.EnumValue::name).toList());
            }
        }
        Declared declared = new Declared(enums, values);
        // A value's own fields are scalars or enums, never another value: nesting is `DOM-014`.
        for (ModuleAst module : syntax.modules()) {
            for (SpecAst.ValueDeclaration source : module.values()) {
                values.put(
                        source.name(),
                        source.fields().stream()
                                .map(field -> field(field, declared))
                                .toList());
            }
        }
        // An operation belongs to the entity its flow names, which need not be the entity of the
        // module it was written in. Grouping happens here so nothing downstream has to care where
        // an operation was typed.
        Map<String, List<SpecAst.UseCaseDeclaration>> byEntity = new LinkedHashMap<>();
        Map<String, String> ownModule = new LinkedHashMap<>();
        for (ModuleAst module : syntax.modules()) {
            if (module.declaresEntity()) {
                ownModule.put(module.entity().name(), module.file());
                byEntity.computeIfAbsent(module.entity().name(), ignored -> new ArrayList<>());
            }
        }
        for (ModuleAst module : syntax.modules()) {
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                String entity = target(useCase)
                        .orElseGet(() -> module.declaresEntity()
                                ? module.entity().name()
                                : null);
                if (entity != null && byEntity.containsKey(entity)) {
                    byEntity.get(entity).add(useCase);
                }
            }
        }

        return new ProjectModel(
                syntax.modules().stream()
                        .flatMap(module -> module.enums().stream())
                        .map(declaration -> new EnumModel(
                                declaration.name(),
                                declaration.values().stream()
                                        .map(SpecAst.EnumValue::name)
                                        .toList(),
                                declaration.where()))
                        .toList(),
                syntax.modules().stream()
                        .flatMap(module -> module.values().stream())
                        .map(declaration -> new ValueModel(
                                declaration.name(),
                                values.getOrDefault(declaration.name(), List.of()),
                                declaration.where()))
                        .toList(),
                syntax.modules().stream()
                        .filter(ModuleAst::declaresEntity)
                        .map(module -> entity(
                                module,
                                byEntity.get(module.entity().name()),
                                bindings,
                                rules,
                                declared,
                                invariants,
                                guards))
                        .toList(),
                logics,
                scenarios);
    }

    /** The entity named by the flow, when it names one. */
    private static Optional<String> target(SpecAst.UseCaseDeclaration useCase) {
        for (SpecAst.FlowStatement statement : useCase.flow()) {
            if (statement instanceof SpecAst.CreateFrom value) {
                return Optional.of(value.entity());
            }
            if (statement instanceof SpecAst.LoadById value) {
                return Optional.of(value.entity());
            }
            if (statement instanceof SpecAst.ListAll value) {
                return Optional.of(value.entity());
            }
        }
        return Optional.empty();
    }

    private static EntityModel entity(
            ModuleAst module,
            List<SpecAst.UseCaseDeclaration> operations,
            BindingModel bindings,
            Map<String, List<RuleModel>> rules,
            Declared declared,
            Map<String, List<RuleModel>> invariants,
            Map<dev.harpia.diag.SourceRef, dev.harpia.logic.TypedExpression> guards) {
        SpecAst.EntityDeclaration declaration = module.entity();
        List<FieldModel> fields = declaration.fields().stream()
                .map(field -> field(field, declared))
                .toList();
        FieldModel idField = fields.stream()
                .filter(field -> field.name().equals("id")
                        && field.type().equals(FieldType.scalar(TypeRef.UUID))
                        && field.generated())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resolver requires semantic validation before resolving "
                                + declaration.name()));
        List<UseCaseModel> useCases = operations.stream()
                .map(useCase -> useCase(useCase, fields, bindings, rules, declared, guards))
                .toList();
        return new EntityModel(
                declaration.name(),
                fields,
                idField,
                useCases,
                invariants.getOrDefault(declaration.name(), List.of()),
                module.where());
    }

    /**
     * A type name is a built-in scalar, or a name the project declared.
     *
     * <p>Semantic validation has already refused any name that resolves to nothing, so what remains
     * here is only the shape of the reference.
     */
    private static FieldType fieldType(String syntax, Declared declared) {
        Optional<String> element = FieldLineParser.elementOf(syntax);
        if (element.isPresent()) {
            return FieldType.list(fieldType(element.orElseThrow(), declared));
        }
        Optional<String> reference = FieldLineParser.referenceOf(syntax);
        if (reference.isPresent()) {
            return FieldType.reference(reference.orElseThrow());
        }
        Optional<String> optional = FieldLineParser.optionalOf(syntax);
        if (optional.isPresent()) {
            return FieldType.optional(fieldType(optional.orElseThrow(), declared));
        }
        if (FieldLineParser.isScalar(syntax)) {
            return FieldType.scalar(TypeRef.fromSyntax(syntax));
        }
        if (declared.values().containsKey(syntax)) {
            return FieldType.value(syntax, declared.values().get(syntax));
        }
        return FieldType.enumeration(syntax, declared.enums().getOrDefault(syntax, List.of()));
    }

    /** The nominal types the project declares, resolved once for the whole run. */
    private record Declared(
            Map<String, List<String>> enums, Map<String, List<FieldModel>> values) {
    }

    private static FieldModel field(
            SpecAst.FieldDeclaration field, Declared declared) {
        return new FieldModel(
                field.name(),
                fieldType(field.type(), declared),
                field.required(),
                field.unique(),
                field.generated(),
                field.defaultValue().map(Literal::new),
                field.where());
    }

    private static UseCaseModel useCase(
            SpecAst.UseCaseDeclaration useCase,
            List<FieldModel> entityFields,
            BindingModel bindings,
            Map<String, List<RuleModel>> rules,
            Declared declared,
            Map<dev.harpia.diag.SourceRef, dev.harpia.logic.TypedExpression> guards) {
        Map<String, FieldModel> fieldsByName = new LinkedHashMap<>();
        entityFields.forEach(field -> fieldsByName.putIfAbsent(field.name(), field));

        List<FieldModel> input = new ArrayList<>();
        for (SpecAst.InputDeclaration declaration : useCase.input()) {
            FieldModel entityField = fieldsByName.get(declaration.name());
            input.add(new FieldModel(
                    declaration.name(),
                    fieldType(declaration.type(), declared),
                    declaration.required(),
                    entityField.unique(),
                    false,
                    entityField.defaultValue(),
                    declaration.where()));
        }

        LinkedHashMap<String, FlowModel.ValueType> variables = new LinkedHashMap<>();
        List<FlowStep> steps = useCase.flow().stream()
                .map(statement -> flowStep(statement, variables, guards))
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
                            case DOMAIN -> ErrorMapping.Condition.DOMAIN;
                        },
                        error.field(),
                        error.name(),
                        error.status(),
                        error.where()))
                .toList();

        return new UseCaseModel(
                nature(useCase.declaredKind()),
                useCase.title(),
                Naming.useCaseBaseName(useCase.title()),
                bindings.bindingFor(Naming.useCaseBaseName(useCase.title())),
                input,
                rules.getOrDefault(Naming.useCaseBaseName(useCase.title()), List.of()),
                new FlowModel(steps, variables),
                new OutputModel(useCase.output().status(), shape),
                errors,
                useCase.where());
    }

    private static OperationNature nature(dev.harpia.parse.DeclarationKind kind) {
        return switch (kind) {
            case COMMAND -> OperationNature.COMMAND;
            case QUERY -> OperationNature.QUERY;
            default -> OperationNature.INFERRED;
        };
    }

    private static FlowStep flowStep(
            SpecAst.FlowStatement statement,
            LinkedHashMap<String, FlowModel.ValueType> variables,
            Map<dev.harpia.diag.SourceRef, dev.harpia.logic.TypedExpression> guards) {
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
        if (statement instanceof SpecAst.Fail value) {
            return new FlowStep.Fail(
                    value.error(),
                    value.text(),
                    guards.get(value.where()),
                    value.where());
        }
        if (statement instanceof SpecAst.Return value) {
            return new FlowStep.Return(value.variable(), value.where());
        }
        throw new IllegalArgumentException("unknown flow statement " + statement.getClass().getName());
    }
}
