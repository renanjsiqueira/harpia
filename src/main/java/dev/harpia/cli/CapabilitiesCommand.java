package dev.harpia.cli;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.capability.ResolvedCapability;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * What this project needs, and who was chosen to supply it.
 *
 * <p>{@code targets} answers what a target can do. This answers the other half: what the
 * specifications actually asked for. A requirement carries the declaration that created it,
 * because "this project needs persistence" is only useful next to the line that made it so.
 */
@Command(
        name = "capabilities",
        description = "Show the capabilities this project requires and their providers.")
public final class CapabilitiesCommand implements Callable<Integer> {

    @Option(names = "--dir", paramLabel = "DIR", description = "Project directory (default: ${DEFAULT-VALUE}).")
    private Path directory = Path.of(".");

    @Option(names = "--json", description = "Report the result as one JSON object on stdout.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(directory, CompileRequest.Mode.VALIDATE));
        int exitCode = CommandSupport.exitCode(result);

        // Capabilities are resolved against the target, so they exist only once the Application IR
        // does. Whenever it is missing the compilation already said why and the exit code already
        // carries it, so the empty list here is never mistaken for "this project needs nothing".
        List<ResolvedCapability> capabilities = result.stages().application()
                .map(application -> List.copyOf(application.capabilities().asMap().values()))
                .orElse(List.of());

        if (json) {
            spec.commandLine().getOut().println(JsonReport.ofCapabilities(
                    exitCode, result.diagnostics(), capabilities));
            spec.commandLine().getOut().flush();
            return exitCode;
        }

        DiagnosticPrinter.print(result.diagnostics(), spec.commandLine().getErr());
        if (result.stages().application().isEmpty()) {
            spec.commandLine().getErr().println(
                    "Capabilities were not resolved; earlier stages failed.");
            spec.commandLine().getErr().flush();
            return exitCode;
        }
        print(spec.commandLine().getOut(), capabilities);
        return exitCode;
    }

    private static void print(PrintWriter out, List<ResolvedCapability> resolved) {
        out.println("Capabilities required by this project");
        if (resolved.isEmpty()) {
            out.println("  none");
            out.flush();
            return;
        }
        for (ResolvedCapability capability : resolved) {
            out.printf(
                    "  %-12s provided by %s%n",
                    capability.capability().id(),
                    capability.provider()
                            .map(dev.harpia.capability.ProviderId::value)
                            .orElse("<target>"));
            capability.requirements().forEach(requirement -> out.printf(
                    "      %s (%s:%d:%d)%n",
                    requirement.reason(),
                    requirement.where().file(),
                    requirement.where().line(),
                    requirement.where().column()));
        }
        out.flush();
    }
}
