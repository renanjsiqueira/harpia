package dev.harpia.validate;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.model.Literals;
import dev.harpia.model.Naming;
import dev.harpia.parse.SpecAst;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Cross-line and cross-file validation performed before the emitter-facing model is built. */
public final class SemanticValidator {

    private SemanticValidator() {
    }

    public static void validate(
            List<SpecAst> specifications, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(specifications, "specifications");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<SpecAst> entities = specifications.stream()
                .filter(SpecAst::declaresEntity)
                .toList();
        validateDuplicateEntities(entities, diagnostics);
        Map<String, SourceRef> routes = new LinkedHashMap<>();
        Map<String, SourceRef> useCaseNames = new LinkedHashMap<>();
        for (SpecAst specification : entities) {
            validateEntity(specification, routes, useCaseNames, diagnostics);
        }
    }

    private static void validateDuplicateEntities(
            List<SpecAst> specifications, DiagnosticCollector diagnostics) {
        Map<String, SpecAst> firstByName = new LinkedHashMap<>();
        Set<String> firstAlreadyReported = new HashSet<>();
        for (SpecAst specification : specifications) {
            SpecAst first = firstByName.putIfAbsent(specification.entityName(), specification);
            if (first == null) {
                continue;
            }
            if (firstAlreadyReported.add(specification.entityName())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_DUPLICATE_ENTITY,
                        "entity '" + specification.entityName() + "' is also declared at "
                                + location(specification.where()),
                        first.where());
            }
            diagnostics.error(
                    ErrorCodes.SEMANTIC_DUPLICATE_ENTITY,
                    "duplicate entity '" + specification.entityName() + "'; first declared at "
                            + location(first.where()),
                    specification.where());
        }
    }

    private static void validateEntity(
            SpecAst specification,
            Map<String, SourceRef> routes,
            Map<String, SourceRef> useCaseNames,
            DiagnosticCollector diagnostics) {
        Map<String, SpecAst.FieldDeclaration> fields = new LinkedHashMap<>();
        for (SpecAst.FieldDeclaration field : specification.fields()) {
            SpecAst.FieldDeclaration first = fields.putIfAbsent(field.name(), field);
            if (first != null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_DUPLICATE_FIELD,
                        "duplicate field '" + field.name() + "'; first declared at "
                                + location(first.where()),
                        field.where());
            }
            validateDefault(field, diagnostics);
        }

        validateId(specification, diagnostics);
        if (specification.useCases().isEmpty()) {
            diagnostics.warning(
                    ErrorCodes.SEMANTIC_ORPHAN_ENTITY,
                    "entity '" + specification.entityName() + "' has no use cases",
                    specification.where());
        }

        for (SpecAst.UseCaseDeclaration useCase : specification.useCases()) {
            String route = useCase.endpoint().method() + " " + useCase.endpoint().path();
            SourceRef firstRoute = routes.putIfAbsent(route, useCase.endpoint().where());
            if (firstRoute != null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_DUPLICATE_ROUTE,
                        "duplicate endpoint '" + route + "'; first declared at " + location(firstRoute),
                        useCase.endpoint().where());
            }
            String baseName = Naming.useCaseBaseName(useCase.title());
            SourceRef firstName = useCaseNames.putIfAbsent(baseName, useCase.where());
            if (firstName != null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_DUPLICATE_USE_CASE,
                        "duplicate use-case name '" + baseName + "'; first declared at "
                                + location(firstName),
                        useCase.where());
            }
            validateInput(useCase, fields, diagnostics);
            validateFlow(specification, useCase, fields, diagnostics);
        }
    }

    private static void validateId(
            SpecAst specification, DiagnosticCollector diagnostics) {
        List<SpecAst.FieldDeclaration> generated = specification.fields().stream()
                .filter(SpecAst.FieldDeclaration::generated)
                .toList();
        boolean valid = generated.size() == 1
                && generated.getFirst().name().equals("id")
                && generated.getFirst().type().equals("UUID")
                && specification.fields().stream()
                        .filter(field -> field.name().equals("id"))
                        .count() == 1;
        if (!valid) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_ID_FIELD,
                    "entity '" + specification.entityName()
                            + "' must declare exactly one '- id: UUID generated' field",
                    specification.where());
        }
    }

    private static void validateDefault(
            SpecAst.FieldDeclaration field, DiagnosticCollector diagnostics) {
        if (field.defaultValue().isEmpty()) {
            return;
        }
        String literal = field.defaultValue().orElseThrow();
        if (!Literals.matches(field.type(), literal)) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_DEFAULT_TYPE,
                    "default literal '" + literal + "' is incompatible with " + field.type(),
                    field.where());
        }
    }

    private static void validateInput(
            SpecAst.UseCaseDeclaration useCase,
            Map<String, SpecAst.FieldDeclaration> fields,
            DiagnosticCollector diagnostics) {
        Set<String> seen = new HashSet<>();
        for (SpecAst.InputDeclaration input : useCase.input()) {
            SpecAst.FieldDeclaration field = fields.get(input.name());
            if (field == null || field.generated() || !seen.add(input.name())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN,
                        "input field '" + input.name()
                                + "' must name one non-generated entity field exactly once",
                        input.where());
                continue;
            }
            if (!field.type().equals(input.type())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_INPUT_FIELD_TYPE,
                        "input field '" + input.name() + "' has type " + input.type()
                                + " but entity field has type " + field.type(),
                        input.where());
            }
        }
    }

    private static void validateFlow(
            SpecAst specification,
            SpecAst.UseCaseDeclaration useCase,
            Map<String, SpecAst.FieldDeclaration> fields,
            DiagnosticCollector diagnostics) {
        Map<String, ValueType> variables = new LinkedHashMap<>();
        boolean loadsById = false;
        boolean createsOrUpdates = false;
        boolean saves = false;

        for (int index = 0; index < useCase.flow().size(); index++) {
            SpecAst.FlowStatement statement = useCase.flow().get(index);
            if (statement instanceof SpecAst.CreateFrom value) {
                validateEntityReference(specification, value.entity(), value.where(), diagnostics);
                define(variables, value.variable(), ValueType.entity(value.entity()), value.where(), diagnostics);
                createsOrUpdates = true;
            } else if (statement instanceof SpecAst.LoadById value) {
                validateEntityReference(specification, value.entity(), value.where(), diagnostics);
                define(variables, value.variable(), ValueType.entity(value.entity()), value.where(), diagnostics);
                loadsById = true;
            } else if (statement instanceof SpecAst.ListAll value) {
                validateEntityReference(specification, value.entity(), value.where(), diagnostics);
                define(variables, value.variable(), ValueType.list(value.entity()), value.where(), diagnostics);
            } else if (statement instanceof SpecAst.UpdateFrom value) {
                requireEntityVariable(variables, value.variable(), value.where(), diagnostics);
                createsOrUpdates = true;
            } else if (statement instanceof SpecAst.Save value) {
                requireEntityVariable(variables, value.variable(), value.where(), diagnostics);
                saves = true;
            } else if (statement instanceof SpecAst.Delete value) {
                requireEntityVariable(variables, value.variable(), value.where(), diagnostics);
            } else if (statement instanceof SpecAst.Return value && value.variable().isPresent()) {
                requireVariable(variables, value.variable().orElseThrow(), value.where(), diagnostics);
            }

            if (statement instanceof SpecAst.Return && index != useCase.flow().size() - 1) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_FLOW_RETURN,
                        "return must be the final flow step",
                        statement.where());
            }
        }

        if (useCase.flow().isEmpty()
                || !(useCase.flow().getLast() instanceof SpecAst.Return returned)) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_FLOW_RETURN,
                    "flow must end with exactly one return",
                    useCase.where());
        } else {
            validateReturn(specification, returned, variables, useCase.output(), diagnostics);
        }

        boolean pathHasId = useCase.endpoint().path().endsWith("/{id}");
        if (pathHasId != loadsById) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_PATH_VAR_FLOW,
                    "endpoint {id} and 'load " + specification.entityName()
                            + " by id' must either both be present or both be absent",
                    useCase.endpoint().where());
        }

        for (SpecAst.ErrorDeclaration error : useCase.errors()) {
            if (error.kind() == SpecAst.ErrorKind.DUPLICATE) {
                Optional<SpecAst.FieldDeclaration> field = error.field().map(fields::get);
                boolean reachable = field.isPresent()
                        && field.orElseThrow().unique()
                        && createsOrUpdates
                        && saves;
                if (!reachable) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_DUPLICATE_ON_NON_UNIQUE,
                            "duplicate error for '" + error.field().orElse("")
                                    + "' is not reachable from a unique field followed by save",
                            error.where());
                }
            }
        }
    }

    private static void validateEntityReference(
            SpecAst specification,
            String referenced,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        if (!referenced.equals(specification.entityName())) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_FOREIGN_ENTITY,
                    "flow in '" + specification.entityName() + "' cannot reference entity '"
                            + referenced + "'",
                    where);
        }
    }

    private static void define(
            Map<String, ValueType> variables,
            String variable,
            ValueType type,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        if (variables.putIfAbsent(variable, type) != null) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_UNDEFINED_VAR,
                    "flow variable '" + variable + "' is defined more than once",
                    where);
        }
    }

    private static Optional<ValueType> requireVariable(
            Map<String, ValueType> variables,
            String variable,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        ValueType type = variables.get(variable);
        if (type == null) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_UNDEFINED_VAR,
                    "flow variable '" + variable + "' was used before it was defined",
                    where);
            return Optional.empty();
        }
        return Optional.of(type);
    }

    private static void requireEntityVariable(
            Map<String, ValueType> variables,
            String variable,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        requireVariable(variables, variable, where, diagnostics).ifPresent(type -> {
            if (type.kind() != ValueKind.ENTITY) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_UNDEFINED_VAR,
                        "flow variable '" + variable + "' is a list, not an entity value",
                        where);
            }
        });
    }

    private static void validateReturn(
            SpecAst specification,
            SpecAst.Return returned,
            Map<String, ValueType> variables,
            SpecAst.Output output,
            DiagnosticCollector diagnostics) {
        boolean matches;
        if (returned.variable().isEmpty()) {
            matches = output.shape().kind() == SpecAst.OutputKind.NOTHING;
        } else {
            ValueType type = variables.get(returned.variable().orElseThrow());
            matches = type != null
                    && type.entity().equals(specification.entityName())
                    && ((type.kind() == ValueKind.ENTITY
                                    && output.shape().kind() == SpecAst.OutputKind.ENTITY)
                            || (type.kind() == ValueKind.LIST
                                    && output.shape().kind() == SpecAst.OutputKind.LIST))
                    && output.shape().entity().filter(type.entity()::equals).isPresent();
        }
        boolean noContentMatches = (output.status() == 204)
                == (output.shape().kind() == SpecAst.OutputKind.NOTHING);
        if (!matches || !noContentMatches) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_RETURN_TYPE,
                    "returned flow value does not match output '" + output.status() + " "
                            + outputDescription(output) + "'",
                    returned.where());
        }
    }

    private static String outputDescription(SpecAst.Output output) {
        return switch (output.shape().kind()) {
            case ENTITY -> output.shape().entity().orElseThrow();
            case LIST -> "List<" + output.shape().entity().orElseThrow() + ">";
            case NOTHING -> "nothing";
        };
    }

    private static String location(SourceRef where) {
        return where.hasPosition()
                ? where.file() + ":" + where.line() + ":" + where.column()
                : where.file();
    }

    private enum ValueKind {
        ENTITY,
        LIST
    }

    private record ValueType(ValueKind kind, String entity) {
        private static ValueType entity(String entity) {
            return new ValueType(ValueKind.ENTITY, entity);
        }

        private static ValueType list(String entity) {
            return new ValueType(ValueKind.LIST, entity);
        }
    }
}
