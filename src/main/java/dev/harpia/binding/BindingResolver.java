package dev.harpia.binding;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.model.AccessRule;
import dev.harpia.model.HttpBinding;
import dev.harpia.model.Naming;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecAst;
import dev.harpia.symbol.Namespace;
import dev.harpia.symbol.SymbolTable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Resolves inline and external syntax into one binding per declared operation. */
public final class BindingResolver {

    private BindingResolver() {
    }

    public static BindingModel resolve(
            ProjectAst project,
            SymbolTable symbols,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Map<String, BindingModel.Http> resolved = new LinkedHashMap<>();

        for (ModuleAst module : project.modules()) {
            for (SpecAst.UseCaseDeclaration operation : module.useCases()) {
                operation.endpoint().ifPresent(endpoint -> register(
                        resolved,
                        new BindingModel.Http(
                                Naming.useCaseBaseName(operation.title()),
                                new HttpBinding(
                                        HttpBinding.HttpMethod.valueOf(endpoint.method()),
                                        "",
                                        endpoint.path(),
                                        endpoint.path().endsWith("/{id}"),
                                        AccessRule.valueOf(
                                                operation.access().orElseThrow().name()),
                                        inferredRequest(operation, endpoint.where()),
                                        inferredResponse(operation),
                                        operation.where(),
                                        endpoint.where()),
                                BindingModel.Origin.INLINE,
                                operation.where(),
                                endpoint.where()),
                        diagnostics));
            }
        }

        for (BindingAst file : project.bindingFiles()) {
            for (BindingAst.Http binding : file.httpBindings()) {
                if (symbols.lookup(Namespace.OPERATIONS, binding.operation()).isEmpty()) {
                    diagnostics.error(
                            ErrorCodes.SEMANTIC_BINDING_OPERATION,
                            "HTTP binding references unknown operation '"
                                    + binding.operation() + "'",
                            binding.where());
                    continue;
                }
                register(
                        resolved,
                        new BindingModel.Http(
                                binding.operation(),
                                new HttpBinding(
                                        HttpBinding.HttpMethod.valueOf(
                                                binding.endpoint().method()),
                                        file.baseUrl().map(BindingAst.BaseUrl::path).orElse(""),
                                        binding.endpoint().path(),
                                        binding.endpoint().path().endsWith("/{id}"),
                                        AccessRule.valueOf(binding.access().name()),
                                        binding.request().stream()
                                                .map(BindingResolver::request)
                                                .toList(),
                                        response(binding.response()),
                                        binding.where(),
                                        binding.endpoint().where()),
                                BindingModel.Origin.EXTERNAL,
                                binding.where(),
                                binding.endpoint().where()),
                        diagnostics);
            }
        }
        return new BindingModel(resolved);
    }

    private static java.util.List<HttpBinding.RequestMapping> inferredRequest(
            SpecAst.UseCaseDeclaration operation,
            dev.harpia.diag.SourceRef where) {
        java.util.List<HttpBinding.RequestMapping> mappings = new java.util.ArrayList<>();
        java.util.Set<String> fromPath = new java.util.LinkedHashSet<>();
        // An inline endpoint has no mapping section, so a parameter binds to the input that shares
        // its name. `id` is the record the flow loads and is not an input.
        for (String parameter : dev.harpia.parse.EndpointParser.parameters(
                operation.endpoint().map(SpecAst.Endpoint::path).orElse(""))) {
            mappings.add(new HttpBinding.Path(parameter, parameter, where));
            if (!parameter.equals("id")) {
                fromPath.add(parameter);
            }
        }
        java.util.List<SpecAst.InputDeclaration> remaining = operation.input().stream()
                .filter(field -> !fromPath.contains(field.name()))
                .toList();
        if (remaining.isEmpty()) {
            return java.util.List.copyOf(mappings);
        }
        // A request that carries no body still has to carry its inputs, and the only other place
        // an inline endpoint has for them is the query string. The name is the mapping there too.
        if (carriesBody(operation)) {
            mappings.add(new HttpBinding.Body("input", operation.where()));
        } else {
            for (SpecAst.InputDeclaration field : remaining) {
                mappings.add(new HttpBinding.Query(field.name(), field.name(), field.where()));
            }
        }
        return java.util.List.copyOf(mappings);
    }

    /**
     * Whether the request has a body to put the remaining inputs in.
     *
     * <p>GET and DELETE state what to act on, not a document to act with. A body on either is
     * something no client expects to send and no proxy promises to forward, so what those verbs
     * carry goes in the URL.
     */
    private static boolean carriesBody(SpecAst.UseCaseDeclaration operation) {
        String method = operation.endpoint().map(SpecAst.Endpoint::method).orElse("");
        return !method.equals("GET") && !method.equals("DELETE");
    }

    private static HttpBinding.ResponseMapping inferredResponse(
            SpecAst.UseCaseDeclaration operation) {
        return operation.output().shape().kind() == SpecAst.OutputKind.NOTHING
                ? new HttpBinding.NoResponse(operation.output().where())
                : new HttpBinding.ResponseBody("output", operation.output().where());
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

    private static void register(
            Map<String, BindingModel.Http> resolved,
            BindingModel.Http binding,
            DiagnosticCollector diagnostics) {
        BindingModel.Http first = resolved.putIfAbsent(binding.operation(), binding);
        if (first != null) {
            diagnostics.error(
                    ErrorCodes.SEMANTIC_DUPLICATE_BINDING,
                    "operation '" + binding.operation() + "' has more than one HTTP binding",
                    binding.where(),
                    "first binding declared here",
                    first.where());
        }
    }
}
