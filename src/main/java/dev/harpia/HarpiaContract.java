package dev.harpia;

import java.util.List;

/**
 * What Harpia promises to callers that are not Harpia.
 *
 * <p>Everything in this compiler is typed, but being typed is not the same as being promised: most
 * of it is free to change with the next slice. This names the part that is not — the types a
 * harness, an editor plugin or an agent actually touches — and puts one number on it, so a consumer
 * can refuse a version it does not know instead of discovering the difference at runtime.
 *
 * <p>{@link #VERSION} is the same number the CLI prints as {@code contract}: the Java API and the
 * JSON output are one promise seen twice, and it would be a lie for them to disagree.
 *
 * <p>Adding a type or a method here is a decision, not an accident, so a test pins the surface and
 * fails until someone either reverts the change or accepts it and raises the version.
 */
public final class HarpiaContract {

    /** Raised only when a change to the surface below would break an existing consumer. */
    public static final int VERSION = 1;

    private HarpiaContract() {
    }

    /**
     * The types a caller outside this compiler is expected to use.
     *
     * <p>Anything reachable from these and not listed is reachable by accident. The order is the
     * order of a caller's questions: compile something, read what came back, look inside it, then
     * ask what this compiler can target.
     */
    public static List<Class<?>> types() {
        return List.of(
                HarpiaContract.class,
                HarpiaCompiler.class,
                CompileRequest.class,
                CompileRequest.Mode.class,
                CompileResult.class,
                CompileResult.Stages.class,
                LanguageVersion.class,
                dev.harpia.diag.Diagnostic.class,
                dev.harpia.diag.Severity.class,
                dev.harpia.diag.SourceRef.class,
                dev.harpia.diag.SourceRef.Position.class,
                dev.harpia.diag.RelatedLocation.class,
                dev.harpia.diag.ErrorCodes.class,
                dev.harpia.emit.GeneratedTree.class,
                dev.harpia.emit.GeneratedFile.class,
                dev.harpia.emit.OutputWriter.class,
                dev.harpia.emit.WriteReport.class,
                dev.harpia.inspect.Inspector.class,
                dev.harpia.inspect.Stage.class,
                dev.harpia.capability.Capability.class,
                dev.harpia.target.TargetCatalog.class,
                dev.harpia.target.TargetDescriptor.class,
                dev.harpia.target.TargetDescriptor.Gate.class,
                dev.harpia.target.TargetId.class,
                dev.harpia.target.TargetStatus.class);
    }
}
