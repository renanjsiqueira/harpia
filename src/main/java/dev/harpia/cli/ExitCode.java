package dev.harpia.cli;

/** Stable process exit codes, also used directly by CLI tests. */
public final class ExitCode {

    public static final int SUCCESS = 0;
    public static final int COMPILATION_ERROR = 1;
    public static final int USAGE_OR_IO_ERROR = 2;

    private ExitCode() {
    }
}
