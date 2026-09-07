package dev.harpia.cli;

import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.SourceRef;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

/** Compiler-style diagnostics suitable for terminals and CI parsers. */
public final class DiagnosticPrinter {

    private DiagnosticPrinter() {
    }

    public static void print(List<Diagnostic> diagnostics, PrintWriter errorWriter) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(errorWriter, "errorWriter");
        diagnostics.forEach(diagnostic -> print(diagnostic, errorWriter));
        errorWriter.flush();
    }

    private static void print(Diagnostic diagnostic, PrintWriter writer) {
        diagnostic.where().ifPresent(where -> writer.print(location(where) + ": "));
        writer.printf("%s[%s]: %s%n",
                diagnostic.severity().label(), diagnostic.code(), diagnostic.message());
        diagnostic.hint().ifPresent(hint -> writer.println("  hint: " + hint));
    }

    private static String location(SourceRef where) {
        return where.hasPosition()
                ? where.file() + ":" + where.line() + ":" + where.column()
                : where.file();
    }
}
