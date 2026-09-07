package dev.harpia.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "version", description = "Print the Harpia compiler version.")
public final class VersionCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try (InputStream stream = VersionCommand.class.getResourceAsStream("/harpia-version.properties")) {
            if (stream == null) {
                throw new IOException("version resource is missing");
            }
            Properties properties = new Properties();
            properties.load(stream);
            spec.commandLine().getOut().println("harpia " + properties.getProperty("version", "unknown"));
            spec.commandLine().getOut().flush();
            return ExitCode.SUCCESS;
        } catch (IOException exception) {
            spec.commandLine().getErr().println(
                    "error[" + dev.harpia.diag.ErrorCodes.IO_FAILURE + "]: " + exception.getMessage());
            spec.commandLine().getErr().flush();
            return ExitCode.USAGE_OR_IO_ERROR;
        }
    }
}
