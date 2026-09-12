package dev.harpia.binding;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.model.HttpBinding;
import dev.harpia.model.IntegrationHttpBinding;
import dev.harpia.parse.ProjectAst;
import dev.harpia.symbol.Symbol;
import dev.harpia.symbol.SymbolTable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Resolves dotted HTTP bindings into outbound Integration adapters. */
public final class IntegrationBindingResolver {

    private IntegrationBindingResolver() {
    }

    public static BindingModel enrich(
            ProjectAst project,
            SymbolTable symbols,
            BindingModel base,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Map<String, BindingModel.IntegrationHttp> resolved = new LinkedHashMap<>(
                base.integrationHttp());
        for (BindingAst file : project.bindingFiles()) {
            for (BindingAst.IntegrationHttp binding : file.integrationHttpBindings()) {
                java.util.Optional<Symbol.Integration> integration =
                        symbols.integration(binding.integration());
                if (integration.isEmpty()) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_BINDING_OPERATION,
                            "HTTP binding references unknown Integration '"
                                    + binding.integration() + "'",
                            binding.where());
                    continue;
                }
                if (integration.orElseThrow().operation(binding.operation()).isEmpty()) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_BINDING_OPERATION,
                            "HTTP binding references unknown Integration operation '"
                                    + binding.target() + "'",
                            binding.where(),
                            "Integration declared here",
                            integration.orElseThrow().where());
                    continue;
                }
                BindingModel.IntegrationHttp model = new BindingModel.IntegrationHttp(
                        binding.target(),
                        new IntegrationHttpBinding(
                                HttpBinding.HttpMethod.valueOf(binding.endpoint().method()),
                                file.baseUrl().map(BindingAst.BaseUrl::path).orElse(""),
                                binding.endpoint().path(),
                                binding.request().stream()
                                        .map(IntegrationBindingResolver::request)
                                        .toList(),
                                response(binding.response()),
                                file.auth().map(IntegrationBindingResolver::auth),
                                binding.where(),
                                binding.endpoint().where()),
                        binding.where(),
                        binding.endpoint().where());
                BindingModel.IntegrationHttp first = resolved.putIfAbsent(
                        binding.target(), model);
                if (first != null) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_DUPLICATE_BINDING,
                            "Integration operation '" + binding.target()
                                    + "' has more than one HTTP binding",
                            binding.where(),
                            "first binding declared here",
                            first.where());
                }
            }
        }
        refuseDisagreement(resolved, diagnostics);
        return new BindingModel(base.http(), resolved);
    }

    private static IntegrationHttpBinding.Auth auth(BindingAst.Auth declared) {
        return new IntegrationHttpBinding.Auth(
                IntegrationHttpBinding.Auth.Kind.valueOf(declared.kind().name()),
                declared.header(),
                declared.where());
    }

    /**
     * One port is reached one way.
     *
     * <p>Two binding files can each bind part of the same Integration, and nothing stops them from
     * declaring different authentication. The generated client is a single object with a single
     * credential, so one of the two declarations would have to be silently dropped — and a call
     * that proves who it is in a way the binding never asked for is worse than a refusal here.
     */
    private static void refuseDisagreement(
            Map<String, BindingModel.IntegrationHttp> resolved, DiagnosticCollector diagnostics) {
        Map<String, BindingModel.IntegrationHttp> first = new LinkedHashMap<>();
        for (BindingModel.IntegrationHttp binding : resolved.values()) {
            String integration = binding.target().substring(0, binding.target().indexOf('.'));
            BindingModel.IntegrationHttp previous = first.putIfAbsent(integration, binding);
            if (previous == null) {
                continue;
            }
            if (!describe(previous).equals(describe(binding))) {
                diagnostics.error(
                        ErrorCodes.SEMANTIC_BINDING_AUTH,
                        "Integration '" + integration + "' is bound with '" + describe(binding)
                                + "' here and '" + describe(previous) + "' elsewhere",
                        binding.where(),
                        "first binding declared here",
                        previous.where());
            }
        }
    }

    private static String describe(BindingModel.IntegrationHttp binding) {
        return binding.binding().auth()
                .map(auth -> switch (auth.kind()) {
                    case BEARER -> "bearer";
                    case API_KEY -> "api key " + auth.header();
                })
                .orElse("no authentication");
    }

    private static HttpBinding.RequestMapping request(BindingAst.RequestMapping mapping) {
        return switch (mapping) {
            case BindingAst.Path value ->
                    new HttpBinding.Path(value.input(), value.parameter(), value.where());
            case BindingAst.Query value ->
                    new HttpBinding.Query(value.input(), value.parameter(), value.where());
            case BindingAst.Header value ->
                    new HttpBinding.Header(value.input(), value.header(), value.where());
            case BindingAst.Body value -> new HttpBinding.Body(value.input(), value.where());
        };
    }

    private static HttpBinding.ResponseMapping response(BindingAst.ResponseMapping mapping) {
        return switch (mapping) {
            case BindingAst.ResponseBody value ->
                    new HttpBinding.ResponseBody(value.output(), value.where());
            case BindingAst.NoResponse value -> new HttpBinding.NoResponse(value.where());
        };
    }
}
