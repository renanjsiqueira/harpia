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
import dev.harpia.parse.SpecAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.source.SourceFile;
import dev.harpia.validate.SemanticValidator;
import java.nio.file.Path;
import java.util.List;

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
        SpecAst specification = SpecParser.parse(source, diagnostics).orElseThrow();
        SemanticValidator.validate(List.of(specification), diagnostics);
        return new CustomerFixture(
                config, Resolver.resolve(List.of(specification), List.of(), List.of()), diagnostics);
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
