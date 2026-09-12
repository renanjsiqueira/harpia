package dev.harpia.cli;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "validate", description = "Validate configuration and specifications without writing files.")
public final class ValidateCommand implements Callable<Integer> {

    @Option(names = "--dir", paramLabel = "DIR", description = "Project directory (default: ${DEFAULT-VALUE}).")
    private Path directory = Path.of(".");

    @Option(names = "--quiet", description = "Suppress the success summary.")
    private boolean quiet;

    @Option(names = "--json", description = "Report the result as one JSON object on stdout.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(directory, CompileRequest.Mode.VALIDATE));
        int exitCode = CommandSupport.exitCode(result);
        // With --json, stdout carries the report and nothing else: a caller that has to parse it
        // cannot be asked to first separate it from prose.
        if (json) {
            spec.commandLine().getOut().println(
                    JsonReport.of("validate", exitCode, result.diagnostics()));
            spec.commandLine().getOut().flush();
            return exitCode;
        }
        DiagnosticPrinter.print(result.diagnostics(), spec.commandLine().getErr());
        if (exitCode == ExitCode.SUCCESS && !quiet) {
            spec.commandLine().getOut().println("Validation succeeded.");
            spec.commandLine().getOut().flush();
        }
        return exitCode;
    }
}
