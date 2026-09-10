package dev.harpia.cli;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.emit.OutputWriter;
import dev.harpia.emit.WriteReport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "build", description = "Compile specifications and synchronize generated output.")
public final class BuildCommand implements Callable<Integer> {

    @Option(names = "--dir", paramLabel = "DIR", description = "Project directory (default: ${DEFAULT-VALUE}).")
    private Path directory = Path.of(".");

    @Option(names = "--clean", description = "Remove stale Harpia-owned files.")
    private boolean clean;

    @Option(names = "--force", description = "Allow clean when unknown files are present.")
    private boolean force;

    @Option(names = "--json", description = "Report the result as one JSON object on stdout.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(directory, CompileRequest.Mode.BUILD));
        int exitCode = CommandSupport.exitCode(result);
        if (exitCode != ExitCode.SUCCESS) {
            return report(exitCode, result.diagnostics());
        }

        DiagnosticCollector writeDiagnostics = new DiagnosticCollector();
        Optional<WriteReport> written = OutputWriter.sync(
                directory,
                result.outputDirectory().orElseThrow(),
                result.tree().orElseThrow(),
                clean,
                force,
                writeDiagnostics);
        // A build says two things: what the compiler decided and what the directory now holds.
        // Both diagnostics belong to the same answer, so the report carries them together.
        List<Diagnostic> diagnostics = new ArrayList<>(result.diagnostics());
        diagnostics.addAll(writeDiagnostics.diagnostics());
        if (written.isEmpty()) {
            return report(ExitCode.USAGE_OR_IO_ERROR, diagnostics);
        }
        if (json) {
            spec.commandLine().getOut().println(JsonReport.of(
                    "build",
                    ExitCode.SUCCESS,
                    diagnostics,
                    result.outputDirectory().orElseThrow(),
                    written.orElseThrow()));
            spec.commandLine().getOut().flush();
            return ExitCode.SUCCESS;
        }
        DiagnosticPrinter.print(diagnostics, spec.commandLine().getErr());
        summarize(written.orElseThrow(), result.outputDirectory().orElseThrow());
        return ExitCode.SUCCESS;
    }

    /** Whichever way this build ended, it ends in the form the caller asked for. */
    private int report(int exitCode, List<Diagnostic> diagnostics) {
        if (json) {
            spec.commandLine().getOut().println(
                    JsonReport.of("build", exitCode, diagnostics));
            spec.commandLine().getOut().flush();
        } else {
            DiagnosticPrinter.print(diagnostics, spec.commandLine().getErr());
        }
        return exitCode;
    }

    private void summarize(WriteReport report, String outputDirectory) {
        var out = spec.commandLine().getOut();
        out.printf(
                "Build succeeded: %d files in %s (%d created, %d updated, %d unchanged).%n",
                report.written(),
                outputDirectory,
                report.created().size(),
                report.updated().size(),
                report.unchanged().size());
        if (!report.deleted().isEmpty()) {
            out.printf("Removed %d stale file(s).%n", report.deleted().size());
        } else if (!report.stale().isEmpty()) {
            out.printf(
                    "%d stale file(s) remain; run with --clean to remove them.%n",
                    report.stale().size());
        }
        out.flush();
    }
}
