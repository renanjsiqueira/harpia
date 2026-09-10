package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.Output;
import dev.harpia.parse.SpecAst.OutputKind;
import dev.harpia.parse.SpecAst.OutputShape;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Grammar for the single line inside {@code ### Output}. */
public final class OutputParser {

    private static final Pattern OUTPUT = Pattern.compile(
            "^(\\d{3}) +(nothing|[A-Z][A-Za-z0-9]*|List<[A-Z][A-Za-z0-9]*>"
                    + "|Page<[A-Z][A-Za-z0-9]*>)$");

    private OutputParser() {
    }

    public static Optional<Output> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        Matcher matcher = OUTPUT.matcher(raw.strip());
        if (!matcher.matches()) {
            return invalid(raw, where, diagnostics);
        }
        int status = Integer.parseInt(matcher.group(1));
        if (status < 200 || status > 299) {
            return invalid(raw, where, diagnostics);
        }
        String value = matcher.group(2);
        OutputShape shape;
        if (value.equals("nothing")) {
            shape = new OutputShape(OutputKind.NOTHING, Optional.empty());
        } else if (value.startsWith("Page<")) {
            shape = new SpecAst.OutputShape(
                    OutputKind.PAGE,
                    Optional.of(value.substring("Page<".length(), value.length() - 1)));
        } else if (value.startsWith("List<")) {
            shape = new OutputShape(
                    OutputKind.LIST, Optional.of(value.substring("List<".length(), value.length() - 1)));
        } else {
            shape = new OutputShape(OutputKind.ENTITY, Optional.of(value));
        }
        return Optional.of(new Output(status, shape, where));
    }

    private static Optional<Output> invalid(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.SYNTAX_USE_CASE_SECTION,
                "invalid output '" + raw + "'; expected a 2xx status and entity, List<Entity>, Page<Entity>, or nothing",
                where);
        return Optional.empty();
    }
}
