package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.Endpoint;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Grammar for the single line inside {@code ### Endpoint}. */
public final class EndpointParser {

    private static final Pattern ENDPOINT = Pattern.compile("^(GET|POST|PUT|DELETE) +([^ ]+)$");
    private static final Pattern PATH = Pattern.compile(
            "^/[a-z][a-z0-9-]*(?:/[a-z][a-z0-9-]*)*(?:/\\{id})?$");

    private EndpointParser() {
    }

    public static Optional<Endpoint> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        Matcher matcher = ENDPOINT.matcher(raw.strip());
        if (!matcher.matches() || !PATH.matcher(matcher.group(2)).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ENDPOINT,
                    "invalid endpoint '" + raw + "'; expected GET|POST|PUT|DELETE and a V0 path",
                    where);
            return Optional.empty();
        }
        return Optional.of(new Endpoint(matcher.group(1), matcher.group(2), where));
    }
}
