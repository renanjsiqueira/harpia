package dev.harpia;

import dev.harpia.application.ApplicationModelBuilder;
import dev.harpia.application.ApplicationProject;
import dev.harpia.application.ProjectSettings;
import dev.harpia.capability.CapabilityAnalyzer;
import dev.harpia.capability.CapabilityResolver;
import dev.harpia.config.ConfigLoader;
import dev.harpia.config.HarpiaConfig;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.model.ProjectModel;
import dev.harpia.model.Resolver;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.source.SourceFile;
import dev.harpia.symbol.SymbolTable;
import dev.harpia.validate.SemanticValidator;
import java.nio.file.Path;

/** The canonical Customer example, compiled up to any stage a test needs. */
public record CustomerFixture(
        HarpiaConfig config,
        ProjectModel business,
        DiagnosticCollector diagnostics) {

    public static CustomerFixture load() {
        Path root = Path.of("examples/customer").toAbsolutePath().normalize();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        HarpiaConfig config = ConfigLoader.load(root, diagnostics).orElseThrow();
        SourceFile source = SourceFile.read(
                        root, root.resolve("specs/customer.harpia.md"), diagnostics)
                .orElseThrow();
        ModuleAst module = SpecParser.parse(
                source, config.harpia().languageVersion(), diagnostics).orElseThrow();
        ProjectAst syntax = new ProjectAst(
                config.harpia().languageVersion(), java.util.List.of(module));
        SymbolTable symbols = SymbolTable.declare(syntax, diagnostics);
        dev.harpia.binding.BindingModel bindings =
                dev.harpia.binding.BindingResolver.resolve(syntax, symbols, diagnostics);
        dev.harpia.binding.BindingValidator.validate(syntax, bindings, diagnostics);
        SemanticValidator.validate(
                syntax,
                symbols,
                bindings,
                diagnostics);
        return new CustomerFixture(
                config,
                Resolver.resolve(
                        syntax,
                        bindings,
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.Map.of()),
                diagnostics);
    }

    public ApplicationProject application() {
        return ApplicationModelBuilder.build(
                ProjectSettings.from(config),
                business,
                CapabilityResolver.resolve(
                                CapabilityAnalyzer.analyze(business), config, diagnostics)
                        .orElseThrow());
    }
}
