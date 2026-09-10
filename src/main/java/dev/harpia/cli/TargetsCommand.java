package dev.harpia.cli;

import dev.harpia.capability.Capability;
import dev.harpia.target.TargetCatalog;
import dev.harpia.target.TargetDescriptor;
import dev.harpia.target.TargetId;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Lists the compilation targets this compiler knows, and what it can actually do with each.
 *
 * <p>It exists so a person or an agent can find out that {@code csharp-aspnet} is catalogued but
 * unavailable, instead of discovering it from a failed build or by guessing.
 */
@Command(name = "targets", description = "List compilation targets and their status.")
public final class TargetsCommand implements Callable<Integer> {

    @Parameters(
            arity = "0..1",
            paramLabel = "TARGET",
            description = "Show details for one target instead of listing all.")
    private String target;

    @Option(names = "--json", description = "Report the result as one JSON object on stdout.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        if (target == null) {
            if (json) {
                out.println(JsonReport.ofTargets(TargetCatalog.all()));
            } else {
                list(out);
            }
            out.flush();
            return ExitCode.SUCCESS;
        }
        Optional<TargetDescriptor> descriptor = descriptor(target);
        if (descriptor.isEmpty()) {
            spec.commandLine().getErr().println("Unknown target: " + target);
            spec.commandLine().getErr().flush();
            // The catalogue is the answer to "then which ones are there", so it follows either
            // way; in JSON it follows as data rather than as a second thing to read.
            if (json) {
                out.println(JsonReport.ofUnknownTarget(target, TargetCatalog.all()));
            } else {
                list(out);
            }
            out.flush();
            return ExitCode.USAGE_OR_IO_ERROR;
        }
        if (json) {
            out.println(JsonReport.ofTarget(descriptor.orElseThrow()));
        } else {
            info(out, descriptor.orElseThrow());
        }
        out.flush();
        return ExitCode.SUCCESS;
    }

    private static Optional<TargetDescriptor> descriptor(String value) {
        try {
            return TargetCatalog.find(TargetId.of(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static void list(PrintWriter out) {
        out.println("Harpia targets");
        out.println();
        out.println("SUPPORTED");
        TargetCatalog.all().stream()
                .filter(descriptor -> descriptor.status().canGenerate())
                .forEach(descriptor -> out.printf(
                        "  %s %-18s %s%n", "✓", descriptor.id(), descriptor.displayName()));
        out.println();
        out.println("NOT SUPPORTED");
        TargetCatalog.all().stream()
                .filter(descriptor -> !descriptor.status().canGenerate())
                .forEach(descriptor -> out.printf(
                        "  %s %-18s %s%n", "-", descriptor.id(), descriptor.displayName()));
    }

    private static void info(PrintWriter out, TargetDescriptor descriptor) {
        out.printf("Target: %s%n", descriptor.displayName());
        out.printf("Id: %s%n", descriptor.id());
        out.printf("Status: %s%n", descriptor.status());
        out.printf("Language: %s%n", descriptor.language());
        out.printf("Framework: %s%n", descriptor.framework());
        out.printf("Language requirement: %s%n", descriptor.languageRequirement());
        out.printf("Target version: %s%n", descriptor.targetVersion());
        if (descriptor.status().canGenerate()) {
            out.printf("Template set: %s (version %d)%n",
                    descriptor.templateSet(), descriptor.templateVersion());
        }
        out.println("Capabilities:");
        if (descriptor.capabilities().isEmpty()) {
            out.println("  none; this target has no generator in this compiler");
            return;
        }
        for (Capability capability : descriptor.orderedCapabilities()) {
            out.printf("  %s%n", capability.id());
        }
    }
}
