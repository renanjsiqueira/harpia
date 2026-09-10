package dev.harpia.cli;

import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.RelatedLocation;
import dev.harpia.diag.SourceRef;
import dev.harpia.capability.Capability;
import dev.harpia.capability.ResolvedCapability;
import dev.harpia.emit.WriteReport;
import dev.harpia.target.TargetDescriptor;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a command decided, as data.
 *
 * <p>The human output is written for someone reading a terminal: it wraps, it aligns, it says
 * "Validation succeeded." A caller that has to act on the result should not have to parse that.
 * This is the same information with nothing arranged for the eye, and {@code contract} says which
 * shape it is, so a consumer can refuse a version it does not know instead of guessing.
 */
final class JsonReport {

    /** Raised only when this file changes shape in a way that breaks an existing consumer. */
    static final int CONTRACT = 1;

    private JsonReport() {
    }

    static String of(String command, int exitCode, List<Diagnostic> diagnostics) {
        return envelope(command, exitCode, diagnostics).render();
    }

    static String of(
            String command,
            int exitCode,
            List<Diagnostic> diagnostics,
            String outputDirectory,
            WriteReport report) {
        return envelope(command, exitCode, diagnostics)
                .put("output", new Json.Object()
                        .put("directory", outputDirectory)
                        .put("written", report.written())
                        .putStrings("created", report.created())
                        .putStrings("updated", report.updated())
                        .putStrings("unchanged", report.unchanged())
                        .putStrings("stale", report.stale())
                        .putStrings("deleted", report.deleted())
                        .putStrings("unknown", report.unknown()))
                .render();
    }

    /** An inspected stage: the same text the terminal shows, carried inside the same envelope. */
    static String ofStage(int exitCode, List<Diagnostic> diagnostics, String stage, String rendered) {
        return envelope("inspect", exitCode, diagnostics)
                .put("stage", stage)
                .put("rendered", rendered)
                .render();
    }

    /** What this compiler can generate, whether or not any project asked for it. */
    static String ofTargets(List<TargetDescriptor> targets) {
        return new Json.Object()
                .put("contract", CONTRACT)
                .put("command", "targets")
                .put("ok", true)
                .put("exitCode", ExitCode.SUCCESS)
                .putObjects("targets", targets.stream().map(JsonReport::target).toList())
                .render();
    }

    /** A name that is not in the catalogue, answered with the catalogue. */
    static String ofUnknownTarget(String requested, List<TargetDescriptor> targets) {
        return new Json.Object()
                .put("contract", CONTRACT)
                .put("command", "targets")
                .put("ok", false)
                .put("exitCode", ExitCode.USAGE_OR_IO_ERROR)
                .put("unknownTarget", requested)
                .putObjects("targets", targets.stream().map(JsonReport::target).toList())
                .render();
    }

    static String ofTarget(TargetDescriptor descriptor) {
        return new Json.Object()
                .put("contract", CONTRACT)
                .put("command", "targets")
                .put("ok", true)
                .put("exitCode", ExitCode.SUCCESS)
                .put("target", target(descriptor))
                .render();
    }

    private static Json.Object target(TargetDescriptor descriptor) {
        Json.Object object = new Json.Object()
                .put("id", descriptor.id().value())
                .put("displayName", descriptor.displayName())
                .put("language", descriptor.language())
                .put("framework", descriptor.framework())
                .put("status", descriptor.status().name())
                .put("canGenerate", descriptor.status().canGenerate())
                .put("languageRequirement", descriptor.languageRequirement())
                .put("minimumLanguageVersion", descriptor.minimumLanguageVersion());
        if (descriptor.status().canGenerate()) {
            object.put("targetVersion", descriptor.targetVersion())
                    .put("templateSet", descriptor.templateSet())
                    .put("templateVersion", descriptor.templateVersion());
        }
        // A target that cannot generate declares none, which is a fact about this compiler and
        // not a gap to be filled in with what someone believes could be implemented.
        return object.putStrings("capabilities", descriptor.orderedCapabilities().stream()
                .map(Capability::id)
                .toList());
    }

    /**
     * What a project needs and who was chosen to supply it.
     *
     * <p>A requirement carries the reason it exists, because "this project needs persistence" is
     * only useful next to the declaration that made it so.
     */
    static String ofCapabilities(
            int exitCode, List<Diagnostic> diagnostics, List<ResolvedCapability> capabilities) {
        return envelope("capabilities", exitCode, diagnostics)
                .putObjects("capabilities", capabilities.stream()
                        .map(JsonReport::capability)
                        .toList())
                .render();
    }

    private static Json.Object capability(ResolvedCapability resolved) {
        Json.Object object = new Json.Object().put("capability", resolved.capability().id());
        // An absent provider is not a missing one: the target implements the capability itself.
        object.put("providedBy", resolved.provider()
                .map(dev.harpia.capability.ProviderId::value)
                .orElse("<target>"));
        return object.putObjects("requiredBy", resolved.requirements().stream()
                .map(requirement -> new Json.Object()
                        .put("reason", requirement.reason())
                        .put("where", location(requirement.where())))
                .toList());
    }

    private static Json.Object envelope(
            String command, int exitCode, List<Diagnostic> diagnostics) {
        return new Json.Object()
                .put("contract", CONTRACT)
                .put("command", command)
                // The exit code is what a shell sees; "ok" is what the exit code means, and a
                // caller should not have to know the numbering to read the answer.
                .put("ok", exitCode == ExitCode.SUCCESS)
                .put("exitCode", exitCode)
                .putObjects("diagnostics", diagnostics.stream().map(JsonReport::diagnostic).toList());
    }

    private static Json.Object diagnostic(Diagnostic diagnostic) {
        Json.Object object = new Json.Object()
                .put("severity", diagnostic.severity().name().toLowerCase(Locale.ROOT))
                .put("code", diagnostic.code())
                .put("message", diagnostic.message());
        diagnostic.where().ifPresent(where -> object.put("where", location(where)));
        object.putIfPresent("hint", diagnostic.hint());
        if (!diagnostic.related().isEmpty()) {
            object.putObjects("related", diagnostic.related().stream()
                    .map(JsonReport::related)
                    .toList());
        }
        return object;
    }

    private static Json.Object related(RelatedLocation related) {
        return new Json.Object()
                .put("message", related.message())
                .put("where", location(related.where()));
    }

    /**
     * A position, with an end only when one is known.
     *
     * <p>Inventing an end would tell a consumer that Harpia measured a span it never measured; an
     * editor that wants to underline asks for the end and falls back to the start when it is not
     * there.
     */
    private static Json.Object location(SourceRef where) {
        Json.Object object = new Json.Object()
                .put("file", where.file())
                .put("line", where.line())
                .put("column", where.column());
        Optional<SourceRef.Position> end = where.end();
        end.ifPresent(position -> object
                .put("endLine", position.line())
                .put("endColumn", position.column()));
        return object;
    }
}
