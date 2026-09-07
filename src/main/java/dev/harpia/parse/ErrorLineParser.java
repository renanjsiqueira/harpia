package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.ErrorDeclaration;
import dev.harpia.parse.SpecAst.ErrorKind;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Closed grammar for V0 error conditions and their mandatory status mappings. */
public final class ErrorLineParser {

    private static final Pattern ERROR = Pattern.compile(
            "^\\s*[-*+] +(invalid input|not found|duplicate +([a-z][A-Za-z0-9]*)) +-> +(\\d{3})\\s*$");

    private ErrorLineParser() {
    }

    public static Optional<ErrorDeclaration> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        Matcher matcher = ERROR.matcher(raw);
        if (!matcher.matches()) {
            return invalid(raw, where, diagnostics);
        }
        String condition = matcher.group(1);
        int status = Integer.parseInt(matcher.group(3));
        ErrorKind kind;
        Optional<String> field = Optional.empty();
        int expected;
        if (condition.equals("invalid input")) {
            kind = ErrorKind.INVALID_INPUT;
            expected = 400;
        } else if (condition.equals("not found")) {
            kind = ErrorKind.NOT_FOUND;
            expected = 404;
        } else {
            kind = ErrorKind.DUPLICATE;
            field = Optional.of(matcher.group(2));
            expected = 409;
        }
        if (status != expected) {
            return invalid(raw, where, diagnostics);
        }
        return Optional.of(new ErrorDeclaration(kind, field, status, where));
    }

    private static Optional<ErrorDeclaration> invalid(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.SYNTAX_ERROR_CONDITION,
                "unknown or invalid error mapping '" + raw
                        + "'; expected invalid input -> 400, duplicate <field> -> 409, or not found -> 404",
                where);
        return Optional.empty();
    }
}
