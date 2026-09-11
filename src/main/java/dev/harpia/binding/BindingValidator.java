package dev.harpia.binding;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.model.Naming;
import dev.harpia.model.HttpBinding;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecAst;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validates resolved bindings against operations and against one another. */
public final class BindingValidator {

    private BindingValidator() {
    }

    public static void validate(
            ProjectAst project,
            BindingModel bindings,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(diagnostics, "diagnostics");

        Map<String, SpecAst.UseCaseDeclaration> operations = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            for (SpecAst.UseCaseDeclaration operation : module.useCases()) {
                operations.putIfAbsent(Naming.useCaseBaseName(operation.title()), operation);
            }
        }

        Map<String, SourceRef> routes = new LinkedHashMap<>();
        for (BindingModel.Http binding : bindings.http().values()) {
            if (binding.binding().baseUrl().matches("https?://.*")) {
                mappingError(
                        "inbound HTTP binding requires a path-prefix '## Base URL', not an "
                                + "absolute URL",
                        binding.binding().where(),
                        diagnostics);
            }
            String route = binding.binding().method() + " " + binding.binding().effectivePath();
            SourceRef first = routes.putIfAbsent(route, binding.endpointWhere());
            if (first != null) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_DUPLICATE_ROUTE,
                        "duplicate endpoint '" + route + "'",
                        binding.endpointWhere(),
                        "first declared here",
                        first);
            }

            SpecAst.UseCaseDeclaration operation = operations.get(binding.operation());
            if (operation == null) {
                continue;
            }
            boolean loadsById = operation.flow().stream()
                    .anyMatch(SpecAst.LoadById.class::isInstance);
            if (binding.binding().hasIdPathVariable() != loadsById) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_PATH_VAR_FLOW,
                        "endpoint {id} and 'load by id' in operation '"
                                + binding.operation()
                                + "' must either both be present or both be absent",
                        binding.endpointWhere());
            }
            validatePathParameters(binding, operation, diagnostics);
            validatePartialUpdate(project.languageVersion(), binding, operation, diagnostics);
            validateAccess(project.languageVersion(), binding, diagnostics);
            validateMappings(binding.binding(), operation, loadsById, diagnostics);
        }
    }

    /**
     * A path parameter is named after the value it carries.
     *
     * <p>{@code id} is the record the flow loads. Any other name is filled from the operation's
     * input, so a name with nothing behind it would leave the segment unbound at request time.
     */
    private static void validatePathParameters(
            BindingModel.Http binding,
            SpecAst.UseCaseDeclaration operation,
            DiagnosticCollector diagnostics) {
        java.util.Set<String> inputs = operation.input().stream()
                .map(SpecAst.InputDeclaration::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (String parameter : dev.harpia.parse.EndpointParser.parameters(
                binding.binding().path())) {
            if (parameter.equals("id") || inputs.contains(parameter)) {
                continue;
            }
            diagnostics.error(
                    ErrorCodes.SEMANTIC_PATH_PARAM_INPUT,
                    "path parameter '{" + parameter + "}' has no input '" + parameter
                            + "' in operation '" + operation.title() + "' to fill it",
                    binding.endpointWhere());
        }
    }

    /**
     * {@code authenticated} is V1.
     *
     * <p>V0 refused it as a feature outside the language. It is inside now, so the refusal moves
     * to where the version is known and says which version admits it.
     */
    private static void validateAccess(
            dev.harpia.LanguageVersion languageVersion,
            BindingModel.Http binding,
            DiagnosticCollector diagnostics) {
        if (binding.binding().access() == dev.harpia.model.AccessRule.AUTHENTICATED
                && languageVersion == dev.harpia.LanguageVersion.V0) {
            diagnostics.error(
                    ErrorCodes.UNSUPPORTED_AUTHENTICATION,
                    "access 'authenticated' needs harpia.languageVersion 1",
                    binding.endpointWhere(),
                    "set harpia.languageVersion to 1");
        }
    }

    /**
     * A partial update states only the changes.
     *
     * <p>That is what separates it from a full update, and everything here follows from it: a
     * field the request may omit cannot also be demanded, and an operation that copies nothing
     * from the request has no changes to state, so calling it partial says nothing about it.
     */
    private static void validatePartialUpdate(
            dev.harpia.LanguageVersion languageVersion,
            BindingModel.Http binding,
            SpecAst.UseCaseDeclaration operation,
            DiagnosticCollector diagnostics) {
        if (binding.binding().method() != HttpBinding.HttpMethod.PATCH) {
            return;
        }
        if (languageVersion == dev.harpia.LanguageVersion.V0) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ENDPOINT,
                    "PATCH needs harpia.languageVersion 1",
                    binding.endpointWhere(),
                    "set harpia.languageVersion to 1");
            return;
        }
        if (operation.flow().stream().noneMatch(SpecAst.UpdateFrom.class::isInstance)) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_PATCH_WITHOUT_UPDATE,
                    "PATCH states a partial update, but operation '" + operation.title()
                            + "' never updates from input",
                    binding.endpointWhere());
        }
        for (SpecAst.InputDeclaration input : operation.input()) {
            if (input.required()) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_PATCH_REQUIRED_INPUT,
                        "input '" + input.name() + "' is required, but PATCH lets the request omit "
                                + "it; a partial update carries only the fields it changes",
                        input.where());
            }
        }
    }

    private static void validateMappings(
            HttpBinding binding,
            SpecAst.UseCaseDeclaration operation,
            boolean loadsById,
            DiagnosticCollector diagnostics) {
        Set<String> fields = new LinkedHashSet<>();
        operation.input().forEach(input -> fields.add(input.name()));
        Set<String> seen = new LinkedHashSet<>();
        boolean body = false;

        for (HttpBinding.RequestMapping mapping : binding.request()) {
            if (!seen.add(mapping.input())) {
                mappingError(
                        "request input '" + mapping.input() + "' is mapped more than once",
                        mapping.where(),
                        diagnostics);
                continue;
            }
            if (mapping instanceof HttpBinding.Body value) {
                body = true;
                if (!value.input().equals("input") || fields.isEmpty()) {
                    mappingError(
                            "body mapping must use 'input' for an operation with declared input",
                            value.where(),
                            diagnostics);
                }
                continue;
            }
            if (mapping.input().equals("id")) {
                if (!loadsById || !(mapping instanceof HttpBinding.Path path)) {
                    mappingError(
                            "'id' must map from a path parameter of an operation that loads by id",
                            mapping.where(),
                            diagnostics);
                } else if (!binding.path().contains("{" + path.parameter() + "}")) {
                    mappingError(
                            "path parameter '" + path.parameter()
                                    + "' does not occur in endpoint '" + binding.path() + "'",
                            path.where(),
                            diagnostics);
                }
            } else if (!fields.contains(mapping.input())) {
                mappingError(
                        "request mapping references unknown input '" + mapping.input() + "'",
                        mapping.where(),
                        diagnostics);
            } else if (mapping instanceof HttpBinding.Path path
                    && !binding.path().contains("{" + path.parameter() + "}")) {
                mappingError(
                        "path parameter '" + path.parameter()
                                + "' does not occur in endpoint '" + binding.path() + "'",
                        path.where(),
                        diagnostics);
            }
        }

        if (body && binding.request().stream().anyMatch(mapping ->
                mapping instanceof HttpBinding.Query || mapping instanceof HttpBinding.Header)) {
            mappingError(
                    "body mapping cannot be mixed with query or header input mappings",
                    binding.where(),
                    diagnostics);
        }
        if (loadsById && !seen.contains("id")) {
            mappingError("request mapping is missing semantic input 'id'", binding.where(), diagnostics);
        }
        if (!fields.isEmpty()) {
            if (!body) {
                for (String field : fields) {
                    if (!seen.contains(field)) {
                        mappingError(
                                "request mapping is missing operation input '" + field + "'",
                                binding.where(),
                                diagnostics);
                    }
                }
            }
        } else if (body) {
            mappingError(
                    "operation has no declared input to map to the request body",
                    binding.where(),
                    diagnostics);
        }

        boolean returnsNothing = operation.output().shape().kind() == SpecAst.OutputKind.NOTHING;
        if (returnsNothing != (binding.response() instanceof HttpBinding.NoResponse)) {
            mappingError(
                    returnsNothing
                            ? "an operation returning nothing must declare response 'none'"
                            : "an operation returning a value must declare 'output: body'",
                    binding.response().where(),
                    diagnostics);
        }
    }

    private static void mappingError(
            String message, SourceRef where, DiagnosticCollector diagnostics) {
        diagnostics.error(ErrorCodes.SEMANTIC_BINDING_MAPPING, message, where);
    }
}
