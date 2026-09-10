package dev.harpia.parse;

import dev.harpia.LanguageVersion;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.parse.Sections.Section;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic dispatch table for H2 declaration parsers.
 *
 * <p>{@link SpecParser} understands modules, not individual declaration syntaxes. Adding a Value,
 * Enum, Command, or Event therefore means registering its parser here rather than extending a
 * monolithic conditional in the module parser. The legacy V0 use case is an explicit fallback
 * because its heading has no kind prefix.
 */
final class DeclarationParserRegistry {

    private final Map<DeclarationKind, DeclarationParser> parsers;
    private final DeclarationParser fallback;

    private DeclarationParserRegistry(
            List<DeclarationParser> declarations, DeclarationParser fallback) {
        Objects.requireNonNull(declarations, "declarations");
        this.fallback = Objects.requireNonNull(fallback, "fallback");

        EnumMap<DeclarationKind, DeclarationParser> byKind =
                new EnumMap<>(DeclarationKind.class);
        for (DeclarationParser parser : declarations) {
            Objects.requireNonNull(parser, "parser");
            if (parser.kind() == fallback.kind()) {
                throw new IllegalArgumentException(
                        "fallback kind " + fallback.kind() + " cannot also be registered");
            }
            if (byKind.putIfAbsent(parser.kind(), parser) != null) {
                throw new IllegalArgumentException(
                        "declaration parser already registered for " + parser.kind());
            }
        }
        this.parsers = java.util.Collections.unmodifiableMap(byKind);
    }

    static DeclarationParserRegistry forLanguageVersion(LanguageVersion languageVersion) {
        Objects.requireNonNull(languageVersion, "languageVersion");
        return switch (languageVersion) {
            case V0 -> new DeclarationParserRegistry(
                    List.of(
                            new DataDeclarationParser(),
                            new LogicDeclarationParser(),
                            new ScenarioDeclarationParser()),
                    new UseCaseDeclarationParser());
            case V1 -> new DeclarationParserRegistry(
                    List.of(
                            new DataDeclarationParser(),
                            new EnumDeclarationParser(),
                            new ValueDeclarationParser(),
                            new InvariantDeclarationParser(),
                            new UseCaseDeclarationParser(DeclarationKind.COMMAND, "Command"),
                            new UseCaseDeclarationParser(DeclarationKind.QUERY, "Query"),
                            new IntegrationDeclarationParser(),
                            new EventDeclarationParser(),
                            new LogicDeclarationParser(),
                            new ScenarioDeclarationParser()),
                    new UseCaseDeclarationParser(DeclarationKind.USE_CASE, null, true));
        };
    }

    DeclarationParser parserFor(String heading) {
        Objects.requireNonNull(heading, "heading");
        List<DeclarationParser> matches = new ArrayList<>();
        for (DeclarationKind kind : DeclarationKind.values()) {
            DeclarationParser parser = parsers.get(kind);
            if (parser != null && parser.recognizes(heading)) {
                matches.add(parser);
            }
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "ambiguous declaration parser registry for '" + heading + "': "
                            + matches.stream().map(DeclarationParser::kind).toList());
        }
        return matches.isEmpty() ? fallback : matches.getFirst();
    }

    Optional<DeclarationAst> parse(
            String moduleName, Section section, DiagnosticCollector diagnostics) {
        return parserFor(section.name()).parse(moduleName, section, diagnostics);
    }

    List<DeclarationKind> kinds() {
        List<DeclarationKind> kinds = new ArrayList<>(parsers.keySet());
        kinds.add(fallback.kind());
        return List.copyOf(kinds);
    }

}
