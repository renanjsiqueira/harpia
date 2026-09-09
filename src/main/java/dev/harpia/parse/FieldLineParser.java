package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.FieldDeclaration;
import dev.harpia.parse.SpecAst.InputDeclaration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Grammar for data and input list items. Literal/type compatibility is resolved semantically. */
public final class FieldLineParser {

    private static final Pattern FIELD = Pattern.compile(
            "^\\s*[-*+] +([a-z][A-Za-z0-9]*): +([^\\s]+)(?: +(.*?))?\\s*$");
    private static final Set<String> TYPES = Set.of(
            "String", "Text", "Int", "Long", "Decimal",
            "Boolean", "UUID", "Email", "Date", "DateTime");

    private FieldLineParser() {
    }

    public static Optional<FieldDeclaration> parseData(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        if (UnsupportedFeatureDetector.relationship(raw)) {
            UnsupportedFeatureDetector.reportRelationship(diagnostics, where);
            return Optional.empty();
        }
        Matcher matcher = FIELD.matcher(raw);
        if (!matcher.matches()) {
            invalid(raw, where, diagnostics, "invalid data field line");
            return Optional.empty();
        }
        String type = matcher.group(2);
        if (!knownType(type)) {
            unknownType(raw, matcher, type, where, diagnostics);
            return Optional.empty();
        }

        boolean required = false;
        boolean unique = false;
        boolean generated = false;
        String defaultValue = null;
        Set<String> seen = new HashSet<>();
        String modifiers = matcher.group(3);
        if (modifiers != null && !modifiers.isBlank()) {
            String remainder = modifiers.strip();
            while (!remainder.isEmpty()) {
                int separator = remainder.indexOf(' ');
                String modifier = separator < 0 ? remainder : remainder.substring(0, separator);
                if (modifier.equals("default")) {
                    if (!seen.add(modifier) || separator < 0 || remainder.substring(separator).isBlank()) {
                        invalid(raw, where, diagnostics, "default requires exactly one literal");
                        return Optional.empty();
                    }
                    defaultValue = remainder.substring(separator).stripLeading();
                    remainder = "";
                } else if (modifier.equals("required")
                        || modifier.equals("unique")
                        || modifier.equals("generated")) {
                    if (!seen.add(modifier)) {
                        invalid(raw, where, diagnostics, "duplicate field modifier '" + modifier + "'");
                        return Optional.empty();
                    }
                    required |= modifier.equals("required");
                    unique |= modifier.equals("unique");
                    generated |= modifier.equals("generated");
                    remainder = separator < 0 ? "" : remainder.substring(separator).stripLeading();
                } else {
                    invalid(raw, where, diagnostics, "unknown field modifier '" + modifier + "'");
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new FieldDeclaration(
                matcher.group(1), type, required, unique, generated,
                Optional.ofNullable(defaultValue), where));
    }

    public static Optional<InputDeclaration> parseInput(
            String raw, SourceRef where, DiagnosticCollector diagnostics) {
        Matcher matcher = FIELD.matcher(raw);
        if (!matcher.matches()) {
            invalid(raw, where, diagnostics, "invalid input field line");
            return Optional.empty();
        }
        String type = matcher.group(2);
        if (!knownType(type)) {
            unknownType(raw, matcher, type, where, diagnostics);
            return Optional.empty();
        }
        String modifiers = matcher.group(3);
        boolean required = modifiers != null && modifiers.strip().equals("required");
        if (modifiers != null && !modifiers.isBlank() && !required) {
            invalid(raw, where, diagnostics, "input fields only accept the 'required' modifier");
            return Optional.empty();
        }
        return Optional.of(new InputDeclaration(matcher.group(1), type, required, where));
    }

    /**
     * Whether the syntax names a type at all.
     *
     * <p>A built-in scalar is known here. A PascalCase name may be a type the project declared, and
     * the parser cannot tell: it has one module, and a declaration lives wherever it was written.
     * So the shape is accepted and whether the name resolves is a semantic question.
     */
    /** Whether the syntax names one of the built-in scalars. */
    public static boolean isScalar(String type) {
        return TYPES.contains(type);
    }

    public static boolean knownType(String type) {
        Optional<String> inner = elementOf(type).or(() -> optionalOf(type));
        return inner.map(FieldLineParser::knownType)
                .orElseGet(() -> TYPES.contains(type) || NOMINAL.matcher(type).matches());
    }

    /** The type an optional wraps, when the syntax names one. */
    public static Optional<String> optionalOf(String type) {
        Matcher matcher = OPTIONAL.matcher(type);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** The element type of a collection, when the syntax names one. */
    public static Optional<String> elementOf(String type) {
        Matcher matcher = LIST.matcher(type);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** A declared type is referenced by its PascalCase name. */
    private static final Pattern NOMINAL = Pattern.compile("[A-Z][A-Za-z0-9]*");
    /** A collection names its element type, which may itself be declared. */
    private static final Pattern LIST = Pattern.compile("List<([^<>]+)>");
    private static final Pattern OPTIONAL = Pattern.compile("Optional<([^<>]+)>");

    private static void unknownType(
            String raw,
            Matcher matcher,
            String type,
            SourceRef where,
            DiagnosticCollector diagnostics) {
        diagnostics.error(
                ErrorCodes.SYNTAX_UNKNOWN_TYPE,
                "unknown type '" + type + "'; expected a declared type or one of "
                        + TYPES.stream().sorted().toList(),
                LineSyntax.at(where, raw, matcher.start(2)));
    }

    private static void invalid(
            String raw, SourceRef where, DiagnosticCollector diagnostics, String reason) {
        diagnostics.error(
                ErrorCodes.SYNTAX_FIELD_LINE,
                reason + ": '" + raw + "'",
                where);
    }
}
