package dev.harpia.cli;

import dev.harpia.CompileResult;
import dev.harpia.diag.ErrorCodes;
import java.util.Set;

final class CommandSupport {

    private static final Set<String> STRUCTURAL_CODES = Set.of(
            ErrorCodes.CONFIG_MISSING,
            ErrorCodes.CONFIG_NO_SPECS,
            ErrorCodes.IO_FAILURE,
            ErrorCodes.IO_PATH_ESCAPE);

    private CommandSupport() {
    }

    static int exitCode(CompileResult result) {
        if (!result.hasErrors()) {
            return ExitCode.SUCCESS;
        }
        return result.diagnostics().stream().anyMatch(diagnostic -> STRUCTURAL_CODES.contains(diagnostic.code()))
                ? ExitCode.USAGE_OR_IO_ERROR
                : ExitCode.COMPILATION_ERROR;
    }
}
