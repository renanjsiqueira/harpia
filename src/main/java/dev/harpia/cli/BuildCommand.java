package dev.harpia.cli;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.emit.OutputWriter;
import dev.harpia.emit.WriteReport;
import java.nio.file.Path;
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

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(directory, CompileRequest.Mode.BUILD));
        DiagnosticPrinter.print(result.diagnostics(), spec.commandLine().getErr());
        int exitCode = CommandSupport.exitCode(result);
        if (exitCode != ExitCode.SUCCESS) {
            return exitCode;
        }

        DiagnosticCollector writeDiagnostics = new DiagnosticCollector();
        Optional<WriteReport> report = OutputWriter.sync(
                directory,
                result.outputDirectory().orElseThrow(),
                result.tree().orElseThrow(),
                clean,
                force,
                writeDiagnostics);
        DiagnosticPrinter.print(writeDiagnostics.diagnostics(), spec.commandLine().getErr());
        if (report.isEmpty()) {
            return ExitCode.USAGE_OR_IO_ERROR;
        }
        summarize(report.orElseThrow(), result.outputDirectory().orElseThrow());
        return ExitCode.SUCCESS;
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
