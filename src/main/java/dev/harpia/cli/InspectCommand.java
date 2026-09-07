package dev.harpia.cli;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Shows one stage of the compiler pipeline.
 *
 * <p>The architectural claims of this project — that the Business IR is target independent, that
 * names resolve deterministically — are checkable from here, without reading generated Java and
 * without trusting the test suite.
 */
@Command(name = "inspect", description = "Show one stage of the compiler pipeline.")
public final class InspectCommand implements Callable<Integer> {

    @Option(names = "--dir", paramLabel = "DIR", description = "Project directory (default: ${DEFAULT-VALUE}).")
    private Path directory = Path.of(".");

    @Option(
            names = "--stage",
            paramLabel = "STAGE",
            required = true,
            description = "One of: ast, symbols, business-ir, application-ir.")
    private String stage;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        Optional<Stage> selected = Stage.find(stage);
        if (selected.isEmpty()) {
            spec.commandLine().getErr().println(
                    "Unknown stage: " + stage + "; expected one of " + Stage.ids());
            spec.commandLine().getErr().flush();
            return ExitCode.USAGE_OR_IO_ERROR;
        }

        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(directory, CompileRequest.Mode.VALIDATE));
        DiagnosticPrinter.print(result.diagnostics(), spec.commandLine().getErr());

        Optional<String> rendered = Inspector.render(result, selected.orElseThrow());
        if (rendered.isEmpty()) {
            spec.commandLine().getErr().printf(
                    "Stage '%s' was not reached; earlier stages failed.%n",
                    selected.orElseThrow().id());
            spec.commandLine().getErr().flush();
            return CommandSupport.exitCode(result) == ExitCode.SUCCESS
                    ? ExitCode.COMPILATION_ERROR
                    : CommandSupport.exitCode(result);
        }
        spec.commandLine().getOut().print(rendered.orElseThrow());
        spec.commandLine().getOut().flush();
        return CommandSupport.exitCode(result);
    }
}
