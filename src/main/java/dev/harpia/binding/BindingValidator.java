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
            validateMappings(binding.binding(), operation, loadsById, diagnostics);
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
            } else if (mapping instanceof HttpBinding.Path) {
                mappingError(
                        "only the semantic 'id' can be mapped from the current path grammar",
                        mapping.where(),
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
