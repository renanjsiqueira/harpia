package dev.harpia.validate;

import dev.harpia.LanguageVersion;
import dev.harpia.binding.BindingModel;
import dev.harpia.binding.BindingResolver;
import dev.harpia.binding.BindingValidator;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.logic.LogicType;
import dev.harpia.model.LogicModel;
import dev.harpia.model.Literals;
import dev.harpia.model.Naming;
import dev.harpia.parse.FieldLineParser;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecAst;
import dev.harpia.parse.UnsupportedFeatureDetector;
import dev.harpia.symbol.Symbol;
import dev.harpia.symbol.SymbolTable;
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
            ProjectAst project,
            SymbolTable symbols,
            DiagnosticCollector diagnostics) {
        BindingModel bindings = BindingResolver.resolve(project, symbols, diagnostics);
        BindingValidator.validate(project, bindings, diagnostics);
        validate(project, symbols, bindings, diagnostics);
    }

    /** Semantic validation when the compiler has already resolved the project's bindings. */
    public static void validate(
            ProjectAst project,
            SymbolTable symbols,
            BindingModel bindings,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(diagnostics, "diagnostics");

        // Duplicate entity and use-case names are the symbol table's business; what remains here is
        // everything that depends on the contents of a declaration rather than on its name.
        Map<String, Entity> entities = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            if (module.declaresEntity()) {
                entities.put(module.entity().name(), validateEntity(module, diagnostics));
                for (SpecAst.FieldDeclaration field : module.entity().fields()) {
                    validateDeclaredType(
                            project.languageVersion(), field.type(), field.where(), symbols,
                            diagnostics);
                    validateOptionality(field.type(), field.required(), field.where(), diagnostics);
                }
            }
        }

        Set<String> targeted = new java.util.LinkedHashSet<>();
        for (ModuleAst module : project.modules()) {
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                validateOperation(
                        project.languageVersion(),
                        module,
                        useCase,
                        entities,
                        symbols,
                        targeted,
                        diagnostics);
            }
        }

        validateDomainErrorStatuses(project, diagnostics);

        // An entity is orphaned when nothing in the project operates on it, which is no longer the
        // same question as whether its own module declares an operation.
        for (ModuleAst module : project.modules()) {
            if (module.declaresEntity() && !targeted.contains(module.entity().name())) {
                diagnostics.warning(
                        ErrorCodes.SEMANTIC_ORPHAN_ENTITY,
                        "entity '" + module.entity().name() + "' has no use cases",
                        module.where());
            }
        }
    }

    /**
     * A domain error is one type across the project, so it answers with one status.
     *
     * <p>Two operations declaring {@code insufficient balance} with different statuses would
     * generate a single exception class whose meaning depends on which handler caught it. That is
     * not a contract, so it is refused rather than resolved by picking a winner.
     */
    private static void validateDomainErrorStatuses(
            ProjectAst project, DiagnosticCollector diagnostics) {
        Map<String, SpecAst.ErrorDeclaration> byName = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            for (SpecAst.UseCaseDeclaration useCase : module.useCases()) {
                for (SpecAst.ErrorDeclaration error : useCase.errors()) {
                    if (error.kind() != SpecAst.ErrorKind.DOMAIN) {
                        continue;
                    }
                    String name = error.name().orElseThrow();
                    SpecAst.ErrorDeclaration first = byName.putIfAbsent(name, error);
                    if (first != null && first.status() != error.status()) {
                        diagnostics.error(
                                ErrorCodes.SEMANTIC_ERROR_STATUS_CONFLICT,
                                "domain error '" + name + "' maps to " + error.status()
                                        + " here and to " + first.status() + " elsewhere",
                                error.where(),
                                "first declared here",
                                first.where());
                    }
                }
            }
        }
    }

    /** The fields of one entity, plus where it was declared, for validating operations on it. */
    private record Entity(
            String name, String module, Map<String, SpecAst.FieldDeclaration> fields) {
    }

    /**
     * A type name that is not a built-in scalar has to be a type the project declared.
     *
     * <p>The parser cannot decide this: it sees one module, and a declaration lives wherever it was
     * written. So the shape is accepted there and the name is resolved here, against every
     * declaration in the project.
     */
    private static void validateDeclaredType(
            LanguageVersion languageVersion,
            String type,
            SourceRef where,
            SymbolTable symbols,
            DiagnosticCollector diagnostics) {
        Optional<String> reference = FieldLineParser.referenceOf(type);
        if (reference.isPresent()) {
            if (languageVersion == LanguageVersion.V0) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_UNKNOWN_TYPE,
                        "Reference needs harpia.languageVersion 1",
                        where,
                        "set harpia.languageVersion to 1");
                return;
            }
            // A reference points at an identity, and only an entity has one.
            if (symbols.entity(reference.orElseThrow()).isEmpty()) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_UNKNOWN_TYPE,
                        "'" + type + "' does not name a declared entity; a reference points at "
                                + "something with an identity",
                        where);
            }
            return;
        }
        Optional<String> optional = FieldLineParser.optionalOf(type);
        if (optional.isPresent()) {
            if (languageVersion == LanguageVersion.V0) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_UNKNOWN_TYPE,
                        "Optional needs harpia.languageVersion 1",
                        where,
                        "set harpia.languageVersion to 1");
                return;
            }
            String inner = optional.orElseThrow();
            if (FieldLineParser.elementOf(inner).isPresent()
                    || FieldLineParser.optionalOf(inner).isPresent()
                    || symbols.entity(inner).isPresent()) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_UNKNOWN_TYPE,
                        "'" + type + "' is not supported; Optional wraps a scalar, a declared enum "
                                + "or a declared value",
                        where);
                return;
            }
            validateDeclaredType(languageVersion, inner, where, symbols, diagnostics);
            return;
        }
        Optional<String> element = FieldLineParser.elementOf(type);
        if (element.isPresent()) {
            if (languageVersion == LanguageVersion.V0) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_UNKNOWN_TYPE,
                        "collections need harpia.languageVersion 1",
                        where,
                        "set harpia.languageVersion to 1");
                return;
            }
            if (symbols.entity(element.orElseThrow()).isPresent()) {
                // A collection of entities is a relationship, which needs a foreign key and a
                // lifecycle Harpia has not specified yet.
                UnsupportedFeatureDetector.reportRelationship(diagnostics, where);
                return;
            }
            // A collection is stored as rows of its element, so a value or another collection
            // would need a shape the element table does not have. Those are DOM-014 and TYPE-025.
            String inner = element.orElseThrow();
            if (symbols.valueType(inner).isPresent() || FieldLineParser.elementOf(inner).isPresent()) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_UNKNOWN_TYPE,
                        "'" + type + "' is not supported; a collection holds a scalar or a "
                                + "declared enum",
                        where);
                return;
            }
            validateDeclaredType(languageVersion, inner, where, symbols, diagnostics);
            return;
        }
        if (FieldLineParser.isScalar(type)
                || symbols.enumType(type).isPresent()
                || symbols.valueType(type).isPresent()) {
            return;
        }
        diagnostics.error(
                ErrorCodes.SEMANTIC_UNKNOWN_TYPE,
                "unknown type '" + type + "'; no declaration in this project provides it",
                where);
    }

    /**
     * {@code Optional<T> required} says a value may be absent and must be present.
     *
     * <p>Refusing it is the point of making optionality a type: the two ways of saying it can now
     * contradict each other, and a contradiction the compiler resolves silently is a bug waiting to
     * be blamed on the generator.
     */
    private static void validateOptionality(
            String type, boolean required, SourceRef where, DiagnosticCollector diagnostics) {
        if (required && FieldLineParser.optionalOf(type).isPresent()) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_OPTIONAL_REQUIRED,
                    "'" + type + "' is declared required; a value cannot be both optional and "
                            + "mandatory",
                    where);
        }
    }

    private static Entity validateEntity(ModuleAst module, DiagnosticCollector diagnostics) {
        Map<String, SpecAst.FieldDeclaration> fields = new LinkedHashMap<>();
        for (SpecAst.FieldDeclaration field : module.entity().fields()) {
            SpecAst.FieldDeclaration first = fields.putIfAbsent(field.name(), field);
            if (first != null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_DUPLICATE_FIELD,
                        "duplicate field '" + field.name() + "'",
                        field.where(),
                        "first declared here",
                        first.where());
            }
            validateDefault(field, diagnostics);
        }

        validateId(module, diagnostics);
        return new Entity(module.entity().name(), module.file(), fields);
    }

    /**
     * Validates one operation against the entity its flow names, which from V1 need not be the
     * entity of its own module.
     */
    private static void validateOperation(
            LanguageVersion languageVersion,
            ModuleAst module,
            SpecAst.UseCaseDeclaration useCase,
            Map<String, Entity> entities,
            SymbolTable symbols,
            Set<String> targeted,
            DiagnosticCollector diagnostics) {
        validateQueryPurity(useCase, diagnostics);

        Optional<Entity> target = target(
                languageVersion, module, useCase, entities, symbols, diagnostics);
        if (target.isEmpty()) {
            return;
        }
        targeted.add(target.orElseThrow().name());
        for (SpecAst.InputDeclaration field : useCase.input()) {
            validateDeclaredType(
                    languageVersion, field.type(), field.where(), symbols, diagnostics);
            validateOptionality(field.type(), field.required(), field.where(), diagnostics);
        }
        validateInput(useCase, target.orElseThrow().fields(), diagnostics);
        validateRules(useCase, diagnostics);
        validateFailures(useCase, diagnostics);
        validateFinds(useCase, target.orElseThrow(), diagnostics);
        validateFlow(target.orElseThrow(), useCase, diagnostics);
    }

    /**
     * A rule is a boolean condition over the operation's input.
     *
     * <p>Its scope is the input and nothing else: a rule that could read the stored entity would be
     * asking a question the operation has not loaded an answer to yet. Reaching further is
     * `RULE-004` and needs the flow to say when the check happens.
     *
     * <p>The condition itself is typed by {@link LogicAnalyzer}, which owns what an expression
     * means. What is checked here is only that the declaration has something to constrain.
     */
    /**
     * A {@code find} names a field that can identify a single record.
     *
     * <p>Finding "the" entity by a field many rows can share is a question with more than one
     * answer, and the flow assigns it to one variable. Uniqueness is what makes the singular true,
     * so it is required rather than hoped for.
     */
    private static void validateFinds(
            SpecAst.UseCaseDeclaration useCase, Entity target, DiagnosticCollector diagnostics) {
        Set<String> inputs = useCase.input().stream()
                .map(SpecAst.InputDeclaration::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (SpecAst.FlowStatement statement : useCase.flow()) {
            if (!(statement instanceof SpecAst.FindBy find)) {
                continue;
            }
            SpecAst.FieldDeclaration field = target.fields().get(find.field());
            if (field == null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN,
                        "entity '" + target.name() + "' has no field '" + find.field() + "'",
                        find.where());
                continue;
            }
            if (!field.unique()) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_FIND_NOT_UNIQUE,
                        "'find " + find.entity() + " by " + find.field()
                                + "' needs '" + find.field() + "' to be unique; "
                                + "otherwise more than one record can answer",
                        find.where());
            }
            if (!inputs.contains(find.field())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN,
                        "'find " + find.entity() + " by " + find.field()
                                + "' has no input '" + find.field() + "' to search with",
                        find.where());
            }
        }
    }

    /**
     * A {@code fail} raises an error the operation declared.
     *
     * <p>The status a domain error answers with lives in {@code ### Errors}. Raising one that was
     * never declared would leave the compiler choosing a status nobody wrote down.
     */
    private static void validateFailures(
            SpecAst.UseCaseDeclaration useCase, DiagnosticCollector diagnostics) {
        Set<String> declared = useCase.errors().stream()
                .filter(error -> error.kind() == SpecAst.ErrorKind.DOMAIN)
                .map(error -> error.name().orElseThrow())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (SpecAst.FlowStatement statement : useCase.flow()) {
            if (statement instanceof SpecAst.Fail fail && !declared.contains(fail.error())) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_FAIL_UNDECLARED,
                        "flow raises '" + fail.error() + "', which operation '" + useCase.title()
                                + "' does not declare in '### Errors'",
                        fail.where());
            }
        }
    }

    private static void validateRules(
            SpecAst.UseCaseDeclaration useCase, DiagnosticCollector diagnostics) {
        if (useCase.rules().isEmpty()) {
            return;
        }
        if (useCase.input().isEmpty()) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_RULE_WITHOUT_INPUT,
                    "operation '" + useCase.title()
                            + "' declares rules but no input for them to constrain",
                    useCase.rules().getFirst().where());
            return;
        }
        // A rule is checked where the flow validates its input. Without that step the rule would be
        // written, compiled and never run, which is worse than not declaring it.
        boolean validates = useCase.flow().stream()
                .anyMatch(statement -> statement instanceof SpecAst.ValidateInput);
        if (!validates) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_RULE_WITHOUT_INPUT,
                    "operation '" + useCase.title()
                            + "' declares rules but its flow never validates input",
                    useCase.rules().getFirst().where(),
                    "add 'validate input' to the flow",
                    useCase.where());
        }
    }

    /**
     * The entity an operation works on: the one its flow names, or the entity of its own module
     * when the flow names none. V0 requires the two to be the same module.
     */
    private static Optional<Entity> target(
            LanguageVersion languageVersion,
            ModuleAst module,
            SpecAst.UseCaseDeclaration useCase,
            Map<String, Entity> entities,
            SymbolTable symbols,
            DiagnosticCollector diagnostics) {
        Map<String, SourceRef> named = new LinkedHashMap<>();
        for (SpecAst.FlowStatement statement : useCase.flow()) {
            referenced(statement).ifPresent(reference ->
                    named.putIfAbsent(reference.name(), reference.where()));
        }
        if (named.size() > 1) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_FOREIGN_ENTITY,
                    "operation '" + useCase.title() + "' works on more than one entity: "
                            + named.keySet(),
                    useCase.where());
            return Optional.empty();
        }
        if (named.isEmpty()) {
            return module.declaresEntity()
                    ? Optional.of(entities.get(module.entity().name()))
                    : missing(useCase, diagnostics);
        }

        String name = named.keySet().iterator().next();
        SourceRef where = named.get(name);
        Entity entity = entities.get(name);
        if (entity == null) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_FOREIGN_ENTITY,
                    "entity '" + name + "' is not declared",
                    where);
            return Optional.empty();
        }
        boolean sameModule = entity.module().equals(module.file());
        if (languageVersion == LanguageVersion.V0 && !sameModule) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_FOREIGN_ENTITY,
                    "Harpia " + languageVersion + " keeps a flow inside the module that declares "
                            + "its entity; '" + name + "' is declared elsewhere",
                    where,
                    "declared here",
                    symbols.entity(name).map(Symbol::where).orElse(where));
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    private static Optional<Entity> missing(
            SpecAst.UseCaseDeclaration useCase, DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.SEMANTIC_FOREIGN_ENTITY,
                "operation '" + useCase.title()
                        + "' names no entity and its module declares none",
                useCase.where());
        return Optional.empty();
    }

    private record Reference(String name, SourceRef where) {
    }

    private static Optional<Reference> referenced(SpecAst.FlowStatement statement) {
        if (statement instanceof SpecAst.CreateFrom value) {
            return Optional.of(new Reference(value.entity(), value.where()));
        }
        if (statement instanceof SpecAst.LoadById value) {
            return Optional.of(new Reference(value.entity(), value.where()));
        }
        if (statement instanceof SpecAst.FindBy value) {
            return Optional.of(new Reference(value.entity(), value.where()));
        }
        if (statement instanceof SpecAst.ListAll value) {
            return Optional.of(new Reference(value.entity(), value.where()));
        }
        return Optional.empty();
    }

    private static void validateId(
            ModuleAst module, DiagnosticCollector diagnostics) {
        List<SpecAst.FieldDeclaration> generated = module.entity().fields().stream()
                .filter(SpecAst.FieldDeclaration::generated)
                .toList();
        boolean valid = generated.size() == 1
                && generated.getFirst().name().equals("id")
                && generated.getFirst().type().equals("UUID")
                && module.entity().fields().stream()
                        .filter(field -> field.name().equals("id"))
                        .count() == 1;
        if (!valid) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_ID_FIELD,
                    "entity '" + module.entity().name()
                            + "' must declare exactly one '- id: UUID generated' field",
                    module.where());
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

    /**
     * A Query declares that it reads. The compiler holds it to that, which is the whole difference
     * between declaring the nature of an operation and inferring it from the flow afterwards.
     */
    private static void validateQueryPurity(
            SpecAst.UseCaseDeclaration useCase, DiagnosticCollector diagnostics) {
        if (useCase.declaredKind() != dev.harpia.parse.DeclarationKind.QUERY) {
            return;
        }
        for (SpecAst.FlowStatement statement : useCase.flow()) {
            String operation = mutating(statement);
            if (operation != null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_QUERY_MUTATES,
                        "Query '" + useCase.title() + "' cannot '" + operation
                                + "'; a Query reads, and a mutation belongs to a Command",
                        statement.where());
            }
        }
    }

    private static String mutating(SpecAst.FlowStatement statement) {
        if (statement instanceof SpecAst.CreateFrom) {
            return "create";
        }
        if (statement instanceof SpecAst.UpdateFrom) {
            return "update";
        }
        if (statement instanceof SpecAst.Save) {
            return "save";
        }
        if (statement instanceof SpecAst.Delete) {
            return "delete";
        }
        return null;
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
            Entity target,
            SpecAst.UseCaseDeclaration useCase,
            DiagnosticCollector diagnostics) {
        Map<String, SpecAst.FieldDeclaration> fields = target.fields();
        Map<String, ValueType> variables = new LinkedHashMap<>();
        boolean createsOrUpdates = false;
        boolean saves = false;

        for (int index = 0; index < useCase.flow().size(); index++) {
            SpecAst.FlowStatement statement = useCase.flow().get(index);
            if (statement instanceof SpecAst.CreateFrom value) {
                define(variables, value.variable(), ValueType.entity(value.entity()), value.where(), diagnostics);
                createsOrUpdates = true;
            } else if (statement instanceof SpecAst.LoadById value) {
                define(variables, value.variable(), ValueType.entity(value.entity()), value.where(), diagnostics);
            } else if (statement instanceof SpecAst.FindBy value) {
                define(variables, value.variable(), ValueType.entity(value.entity()), value.where(), diagnostics);
            } else if (statement instanceof SpecAst.ListAll value) {
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
            validateReturn(target.name(), returned, variables, useCase.output(), diagnostics);
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
            String entityName,
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
                    && type.entity().equals(entityName)
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
