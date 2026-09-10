package dev.harpia.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import java.util.concurrent.Callable;

@Command(
        name = "harpia",
        description = "Compile executable Harpia specifications.",
        mixinStandardHelpOptions = true,
        subcommands = {
            ValidateCommand.class,
            BuildCommand.class,
            InspectCommand.class,
            TargetsCommand.class,
            CapabilitiesCommand.class,
            VersionCommand.class
        })
public final class HarpiaCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return ExitCode.USAGE_OR_IO_ERROR;
    }
}
