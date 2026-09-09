package dev.harpia;

import dev.harpia.application.ApplicationModelBuilder;
import dev.harpia.application.ApplicationProject;
import dev.harpia.application.ProjectSettings;
import dev.harpia.application.ProviderValidation;
import dev.harpia.binding.BindingAst;
import dev.harpia.binding.BindingModel;
import dev.harpia.binding.BindingParser;
import dev.harpia.binding.BindingResolver;
import dev.harpia.binding.BindingValidator;
import dev.harpia.capability.CapabilityAnalyzer;
import dev.harpia.capability.CapabilityRequirementSet;
import dev.harpia.capability.CapabilityResolver;
import dev.harpia.capability.ResolvedCapabilities;
import dev.harpia.config.ConfigLoader;
import dev.harpia.config.HarpiaConfig;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.emit.GeneratedTree;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.Resolver;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.source.SourceFile;
import dev.harpia.source.BindingDiscovery;
import dev.harpia.source.SpecDiscovery;
import dev.harpia.symbol.SymbolTable;
import dev.harpia.target.HarpiaTarget;
import dev.harpia.target.TargetGenerationException;
import dev.harpia.target.TargetGenerationResult;
import dev.harpia.target.TargetRegistry;
import dev.harpia.target.TargetResolution;
import dev.harpia.target.TargetResolver;
import dev.harpia.validate.LogicAnalyzer;
import dev.harpia.validate.SemanticValidator;
import dev.harpia.validate.TestCoverage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stateless, non-writing compiler entry point shared by {@code validate} and {@code build}.
 *
 * <pre>
 * config -&gt; parse -&gt; semantic analysis -&gt; Business IR -&gt; capability requirements
 *        -&gt; target resolution -&gt; capability resolution -&gt; Application IR
 *        -&gt; target validation -&gt; target generation
 * </pre>
 *
 * <p>Everything before target resolution is target independent: the same specification produces the
 * same Business IR whichever target is configured.
 */
public final class HarpiaCompiler {

    private final TargetRegistry targets;

    public HarpiaCompiler() {
        this(TargetRegistry.standard());
    }

    public HarpiaCompiler(TargetRegistry targets) {
        this.targets = Objects.requireNonNull(targets, "targets");
    }

    public CompileResult compile(CompileRequest request) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        Optional<HarpiaConfig> loaded = ConfigLoader.load(request.projectRoot(), diagnostics);
        LanguageVersion languageVersion = loaded
                .map(config -> config.harpia().languageVersion())
                .orElse(LanguageVersion.CURRENT);
        List<ModuleAst> modules = new ArrayList<>();
        List<BindingAst> bindingFiles = new ArrayList<>();

        if (loaded.isPresent()) {
            HarpiaConfig config = loaded.orElseThrow();
            Path specsDirectory = Path.of(config.paths().specs());
            for (Path sourcePath : SpecDiscovery.discover(
                    request.projectRoot(), specsDirectory, diagnostics)) {
                SourceFile.read(request.projectRoot(), sourcePath, diagnostics)
                        .flatMap(source -> SpecParser.parse(source, languageVersion, diagnostics))
                        .ifPresent(modules::add);
            }
            Path bindingsDirectory = Path.of(config.paths().bindings());
            for (Path sourcePath : BindingDiscovery.discover(
                    request.projectRoot(), bindingsDirectory, diagnostics)) {
                SourceFile.read(request.projectRoot(), sourcePath, diagnostics)
                        .flatMap(source -> BindingParser.parse(
                                source, languageVersion, diagnostics))
                        .ifPresent(bindingFiles::add);
            }
        }

        ProjectAst syntax = new ProjectAst(languageVersion, modules, bindingFiles);
        CompileResult.Stages stages = CompileResult.Stages.parsed(syntax);
        if (diagnostics.hasErrors()) {
            return failed(stages, diagnostics);
        }

        SymbolTable symbols = SymbolTable.declare(syntax, diagnostics);
        stages = stages.withSymbols(symbols);
        BindingModel bindings = BindingResolver.resolve(syntax, symbols, diagnostics);
        BindingValidator.validate(syntax, bindings, diagnostics);
        SemanticValidator.validate(syntax, symbols, bindings, diagnostics);
        LogicAnalyzer.Result computations =
                LogicAnalyzer.analyze(syntax, symbols, diagnostics);
        if (diagnostics.hasErrors()) {
            return failed(stages, diagnostics);
        }

        HarpiaConfig config = loaded.orElseThrow();
        ProjectModel business = Resolver.resolve(
                syntax,
                bindings,
                computations.logics(),
                computations.scenarios(),
                computations.rules(),
                computations.invariants());
        TestCoverage.report(business, config.generation().tests(), diagnostics);
        stages = stages.withBusiness(business);
        CapabilityRequirementSet requirements = CapabilityAnalyzer.analyze(business);

        Optional<TargetResolution> resolution = TargetResolver.resolve(
                targets,
                config,
                request.mode() == CompileRequest.Mode.BUILD
                        ? TargetResolver.Stage.GENERATION
                        : TargetResolver.Stage.VALIDATION,
                diagnostics);
        if (resolution.isEmpty() || diagnostics.hasErrors()) {
            return failed(stages, diagnostics);
        }

        TargetResolution target = resolution.orElseThrow();
        if (target.canGenerate()
                && !TargetResolver.supportsEveryRequirement(
                        target.descriptor(), requirements, diagnostics)) {
            return failed(stages, diagnostics);
        }

        Optional<ResolvedCapabilities> capabilities =
                CapabilityResolver.resolve(requirements, config, diagnostics);
        if (capabilities.isEmpty()) {
            return failed(stages, diagnostics);
        }

        ApplicationProject application = ApplicationModelBuilder.build(
                ProjectSettings.from(config), business, capabilities.orElseThrow());
        ProviderValidation.validate(application, diagnostics);
        stages = stages.withApplication(application);
        if (diagnostics.hasErrors()) {
            return failed(stages, diagnostics);
        }

        if (!target.canGenerate()) {
            // The specification is valid; this compiler simply has no generator for the target.
            return new CompileResult(Optional.empty(), stages, diagnostics.diagnostics());
        }

        HarpiaTarget generator = target.generator().orElseThrow();
        generator.validate(application, target.configuration(), diagnostics);
        if (diagnostics.hasErrors()) {
            return failed(stages, diagnostics);
        }

        TargetGenerationResult generation;
        try {
            generation = generator.generate(application, target.configuration());
        } catch (TargetGenerationException exception) {
            diagnostics.error(exception.code(), exception.getMessage(), exception.source());
            return failed(stages, diagnostics);
        }
        GeneratedTree generated = generation.toTree();
        return new CompileResult(
                Optional.of(generated),
                Optional.of(config.paths().output()),
                stages,
                diagnostics.diagnostics());
    }

    private static CompileResult failed(
            CompileResult.Stages stages, DiagnosticCollector diagnostics) {
        return new CompileResult(Optional.empty(), stages, diagnostics.diagnostics());
    }
}
