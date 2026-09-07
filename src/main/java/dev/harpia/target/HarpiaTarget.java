package dev.harpia.target;

import dev.harpia.application.ApplicationProject;
import dev.harpia.diag.DiagnosticCollector;

/**
 * The generation boundary of the compiler.
 *
 * <p>Everything above this interface is target independent: parser, semantic model, Business IR,
 * Application IR and capability resolution. Everything a target does with an Application IR —
 * choosing an architecture, mapping types, naming classes, writing a build file — lives behind it.
 *
 * <p>A target that cannot generate is not represented by an implementation that throws. It exists
 * only as a {@link TargetDescriptor} in the catalogue, and {@link TargetResolver} reports it.
 */
public interface HarpiaTarget {

    TargetDescriptor descriptor();

    /**
     * Reports constraints that only this target imposes, such as identifiers reserved by its
     * language. The Application IR is already valid Harpia when this runs.
     */
    void validate(
            ApplicationProject application,
            TargetConfiguration configuration,
            DiagnosticCollector diagnostics);

    /** Produces the complete, deterministic file tree for this target. */
    TargetGenerationResult generate(
            ApplicationProject application, TargetConfiguration configuration);
}
