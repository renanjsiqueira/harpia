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

    private static final Pattern ENDPOINT = Pattern.compile("^(GET|POST|PUT|PATCH|DELETE) +([^ ]+)$");
    /**
     * A path is literal segments and named parameters, in any order.
     *
     * <p>A parameter is named after the value it carries, so the name is the mapping: nothing else
     * has to say which input a segment fills.
     */
    private static final Pattern PATH = Pattern.compile(
            "^(?:/(?:[a-z][a-z0-9-]*|\\{[a-z][A-Za-z0-9]*}))+$");

    /** The names a path binds, in the order they appear. */
    public static java.util.List<String> parameters(String path) {
        java.util.Objects.requireNonNull(path, "path");
        java.util.List<String> names = new java.util.ArrayList<>();
        Matcher matcher = PARAMETER.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return java.util.List.copyOf(names);
    }

    private static final Pattern PARAMETER = Pattern.compile("\\{([a-z][A-Za-z0-9]*)}");

    private EndpointParser() {
    }

    public static Optional<Endpoint> parse(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        Matcher matcher = ENDPOINT.matcher(raw.strip());
        if (!matcher.matches() || !PATH.matcher(matcher.group(2)).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ENDPOINT,
                    "invalid endpoint '" + raw + "'; expected GET|POST|PUT|PATCH|DELETE and a path of "
                            + "lower-case segments and {parameters}",
                    where);
            return Optional.empty();
        }
        return Optional.of(new Endpoint(matcher.group(1), matcher.group(2), where));
    }
}
