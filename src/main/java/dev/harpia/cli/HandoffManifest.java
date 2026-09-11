package dev.harpia.cli;

import dev.harpia.HarpiaContract;
import dev.harpia.application.ApplicationProject;
import dev.harpia.capability.ResolvedCapability;
import dev.harpia.emit.OutputWriter;
import dev.harpia.emit.WriteReport;
import dev.harpia.target.TargetCatalog;
import dev.harpia.target.TargetDescriptor;
import dev.harpia.target.TargetId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What someone continuing in the generated code needs to know.
 *
 * <p>A build leaves a directory behind, and the person or agent that picks it up next has to answer
 * the same questions every time: which target is this, what does the project depend on, which files
 * are mine to edit, and what did Harpia deliberately leave unimplemented. Those answers exist
 * inside the compiler and used to stop at the terminal.
 *
 * <p>It carries no timestamp. Two identical builds produce identical bytes everywhere else in
 * Harpia, and a clock here would be the one thing that made a rebuild look like a change.
 */
final class HandoffManifest {

    static final String PATH = ".harpia/handoff.json";

    private HandoffManifest() {
    }

    static void write(
            Path projectRoot,
            String outputDirectory,
            ApplicationProject application,
            TargetId target,
            WriteReport report) throws IOException {
        Path file = projectRoot.resolve(PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, render(outputDirectory, application, target, report) + "\n",
                StandardCharsets.UTF_8);
    }

    static String render(
            String outputDirectory,
            ApplicationProject application,
            TargetId target,
            WriteReport report) {
        return new Json.Object()
                .put("contract", HarpiaContract.VERSION)
                .put("project", new Json.Object()
                        .put("name", application.settings().name())
                        .put("namespace", application.settings().namespace()))
                .put("target", target(target))
                .put("output", new Json.Object()
                        .put("directory", outputDirectory)
                        // The manifest already lists every generated path, so this points at it
                        // instead of keeping a second copy that could disagree with the first.
                        .put("manifest", outputDirectory + "/" + OutputWriter.MANIFEST)
                        .put("files", report.written()))
                .putObjects("capabilities", application.capabilities().asMap().values().stream()
                        .map(HandoffManifest::capability)
                        .toList())
                .putObjects("customContracts", customContracts(application))
                .put("ownership", new Json.Object()
                        .put("generated", "listed in " + OutputWriter.MANIFEST
                                + "; rewritten on every build")
                        .put("yours", "any file the manifest does not list, including custom "
                                + "contract implementations; never overwritten and never cleaned"))
                .render();
    }

    private static Json.Object target(TargetId id) {
        Optional<TargetDescriptor> descriptor = TargetCatalog.find(id);
        Json.Object object = new Json.Object().put("id", id.value());
        descriptor.ifPresent(present -> object
                .put("status", present.status().name())
                .put("language", present.language())
                .put("framework", present.framework())
                .put("languageRequirement", present.languageRequirement()));
        return object;
    }

    private static Json.Object capability(ResolvedCapability resolved) {
        return new Json.Object()
                .put("capability", resolved.capability().id())
                // An absent provider is not a missing one: the target implements it itself.
                .put("providedBy", resolved.provider()
                        .map(dev.harpia.capability.ProviderId::value)
                        .orElse("<target>"));
    }

    /**
     * The work Harpia deliberately did not do.
     *
     * <p>A {@code custom} Logic generates its interface and stops: the body is a decision the
     * specification refused to make, so it is the one thing a handoff has to name explicitly or
     * the project looks finished and will not start.
     */
    private static List<Json.Object> customContracts(ApplicationProject application) {
        return application.logics().stream()
                .filter(logic -> logic.customContract().isPresent())
                .map(logic -> new Json.Object()
                        .put("logic", logic.typeName())
                        .put("contract", logic.customContract().orElseThrow())
                        .put("implement", "a bean implementing "
                                + application.settings().namespace() + ".logic."
                                + logic.customContract().orElseThrow()))
                .toList();
    }
}
