package dev.harpia.binding;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.model.HttpBinding;
import dev.harpia.model.IntegrationHttpBinding;
import dev.harpia.parse.IntegrationAst;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Checks outbound HTTP mappings against their Integration operation signatures. */
public final class IntegrationBindingValidator {

    private IntegrationBindingValidator() {
    }

    public static void validate(
            ProjectAst project, BindingModel bindings, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Map<String, IntegrationAst.Operation> operations = new LinkedHashMap<>();
        for (ModuleAst module : project.modules()) {
            for (IntegrationAst.Declaration integration : module.integrations()) {
                for (IntegrationAst.Operation operation : integration.operations()) {
                    operations.putIfAbsent(
                            integration.name() + "." + operation.name(), operation);
                }
            }
        }
        bindings.integrationHttp().forEach((target, resolved) -> {
            IntegrationAst.Operation operation = operations.get(target);
            if (operation != null) {
                validate(resolved.binding(), target, operation, diagnostics);
            }
        });
    }

    private static void validate(
            IntegrationHttpBinding binding,
            String target,
            IntegrationAst.Operation operation,
            DiagnosticCollector diagnostics) {
        if (!binding.baseUrl().matches("https?://.*")) {
            mappingError(
                    "outbound HTTP binding for '" + target
                            + "' requires an absolute http(s) '## Base URL'",
                    binding.where(),
                    diagnostics);
        }
        Set<String> parameters = operation.input().stream()
                .map(dev.harpia.parse.SpecAst.InputDeclaration::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> seen = new LinkedHashSet<>();
        Set<String> mappedPath = new LinkedHashSet<>();
        boolean body = false;
        for (HttpBinding.RequestMapping mapping : binding.request()) {
            if (!seen.add(mapping.input())) {
                mappingError(
                        "Integration input '" + mapping.input() + "' is mapped more than once",
                        mapping.where(),
                        diagnostics);
                continue;
            }
            if (mapping instanceof HttpBinding.Body value) {
                body = true;
                if (!value.input().equals("input") || parameters.isEmpty()) {
                    mappingError(
                            "body mapping must use 'input' for an Integration operation with "
                                    + "declared input",
                            value.where(),
                            diagnostics);
                }
                continue;
            }
            if (!parameters.contains(mapping.input())) {
                mappingError(
                        "request mapping references unknown Integration input '"
                                + mapping.input() + "'",
                        mapping.where(),
                        diagnostics);
            }
            if (mapping instanceof HttpBinding.Path path) {
                mappedPath.add(path.parameter());
                if (!binding.path().contains("{" + path.parameter() + "}")) {
                    mappingError(
                            "path parameter '" + path.parameter()
                                    + "' does not occur in outbound endpoint '"
                                    + binding.path() + "'",
                            path.where(),
                            diagnostics);
                }
            }
        }
        if (body && (binding.method() == HttpBinding.HttpMethod.GET
                || binding.method() == HttpBinding.HttpMethod.DELETE)) {
            mappingError(
                    binding.method() + " outbound binding cannot carry a request body",
                    binding.endpointWhere(),
                    diagnostics);
        }
        if (body && binding.request().stream().anyMatch(mapping ->
                mapping instanceof HttpBinding.Query || mapping instanceof HttpBinding.Header)) {
            mappingError(
                    "body mapping cannot be mixed with query or header input mappings",
                    binding.where(),
                    diagnostics);
        }
        if (!body) {
            for (String parameter : parameters) {
                if (!seen.contains(parameter)) {
                    mappingError(
                            "request mapping is missing Integration input '" + parameter + "'",
                            binding.where(),
                            diagnostics);
                }
            }
        }
        for (String parameter : dev.harpia.parse.EndpointParser.parameters(binding.path())) {
            if (!mappedPath.contains(parameter)) {
                mappingError(
                        "outbound path parameter '{" + parameter + "}' has no path mapping",
                        binding.endpointWhere(),
                        diagnostics);
            }
        }
        boolean returnsNothing = operation.output().returnsNothing();
        if (returnsNothing != (binding.response() instanceof HttpBinding.NoResponse)) {
            mappingError(
                    returnsNothing
                            ? "an Integration operation returning nothing must declare response "
                                    + "'none'"
                            : "an Integration operation returning a value must declare "
                                    + "'output: body'",
                    binding.response().where(),
                    diagnostics);
        }
    }

    private static void mappingError(
            String message,
            dev.harpia.diag.SourceRef where,
            DiagnosticCollector diagnostics) {
        diagnostics.error(ErrorCodes.SEMANTIC_BINDING_MAPPING, message, where);
    }
}
