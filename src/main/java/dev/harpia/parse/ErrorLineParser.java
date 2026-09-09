package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.ErrorDeclaration;
import dev.harpia.parse.SpecAst.ErrorKind;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grammar for the error conditions a use case declares and the status each maps to.
 *
 * <p>Three conditions are detected by the runtime — an invalid input, a missing record, a unique
 * violation — so the compiler knows exactly when they occur and fixes their status. Anything else
 * is a domain error: the business names it, declares what it means over HTTP, and the code raises
 * it. Naming one is what makes it a type instead of a status literal buried in a handler.
 */
public final class ErrorLineParser {

    private static final Pattern LINE = Pattern.compile(
            "^\\s*[-*+] +(\\S.*?) +-> +(\\d{3})\\s*$");
    private static final Pattern DUPLICATE = Pattern.compile("duplicate +([a-z][A-Za-z0-9]*)");
    private static final Pattern DOMAIN_NAME = Pattern.compile("[a-z]+(?: [a-z]+)*");

    private ErrorLineParser() {
    }

    public static Optional<ErrorDeclaration> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        Matcher matcher = LINE.matcher(raw);
        if (!matcher.matches()) {
            return invalid(raw, where, diagnostics);
        }
        String condition = matcher.group(1);
        int status = Integer.parseInt(matcher.group(2));

        if (condition.equals("invalid input")) {
            return fixed(ErrorKind.INVALID_INPUT, Optional.empty(), status, 400, raw, where,
                    diagnostics);
        }
        if (condition.equals("not found")) {
            return fixed(ErrorKind.NOT_FOUND, Optional.empty(), status, 404, raw, where,
                    diagnostics);
        }
        Matcher duplicate = DUPLICATE.matcher(condition);
        if (duplicate.matches()) {
            return fixed(ErrorKind.DUPLICATE, Optional.of(duplicate.group(1)), status, 409, raw,
                    where, diagnostics);
        }
        if (condition.startsWith("duplicate")) {
            return invalid(raw, where, diagnostics);
        }
        return domain(condition, status, raw, where, diagnostics);
    }

    private static Optional<ErrorDeclaration> fixed(
            ErrorKind kind,
            Optional<String> field,
            int status,
            int expected,
            String raw,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        if (status != expected) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ERROR_CONDITION,
                    "'" + raw.strip() + "' declares a status the condition does not have; "
                            + "a detected condition maps to " + expected,
                    where);
            return Optional.empty();
        }
        return Optional.of(new ErrorDeclaration(kind, field, Optional.empty(), status, where));
    }

    private static Optional<ErrorDeclaration> domain(
            String condition,
            int status,
            String raw,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        if (!DOMAIN_NAME.matcher(condition).matches()) {
            return invalid(raw, where, diagnostics);
        }
        // A failure that answers 2xx or 3xx is not a failure, and 1xx cannot end a request.
        if (status < 400 || status > 599) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ERROR_CONDITION,
                    "domain error '" + condition + "' maps to " + status
                            + "; an error status is 4xx or 5xx",
                    where);
            return Optional.empty();
        }
        return Optional.of(new ErrorDeclaration(
                ErrorKind.DOMAIN, Optional.empty(), Optional.of(condition), status, where));
    }

    private static Optional<ErrorDeclaration> invalid(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.SYNTAX_ERROR_CONDITION,
                "unknown or invalid error mapping '" + raw.strip()
                        + "'; expected invalid input -> 400, duplicate <field> -> 409, "
                        + "not found -> 404, or a domain error such as "
                        + "'insufficient balance -> 422'",
                where);
        return Optional.empty();
    }
}
