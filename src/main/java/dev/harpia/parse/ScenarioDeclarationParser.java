package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.Sections.Section;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses one {@code ## Scenario Title} declaration.
 *
 * <p>A scenario is the only thing that can say what a pure computation <em>should</em> return: the
 * compiler can prove the types of {@code CalculateDiscount}, but not which number is correct. The
 * expected value has to come from a person, and this is where they write it.
 */
public final class ScenarioDeclarationParser {

    public static final String PREFIX = "Scenario";
    private static final Pattern DECLARATION =
            Pattern.compile("^Scenario +([A-Za-z][A-Za-z0-9]*(?: +[A-Za-z0-9]+)*)$");
    private static final Pattern BINDING =
            Pattern.compile("^\\s*[-*+] +([a-z][A-Za-z0-9]*): +(.+?)\\s*$");
    private static final Pattern COMPUTATION = Pattern.compile("^[A-Z][A-Za-z0-9]*$");
    private static final String RESULT = "result";
    private static final Set<String> SUBSECTIONS = Set.of("Given", "When", "Then");

    private ScenarioDeclarationParser() {
    }

    public static boolean declares(String heading) {
        return heading.equals(PREFIX) || heading.startsWith(PREFIX + " ");
    }

    public static Optional<LogicAst.Scenario> parse(
            Section section, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(diagnostics, "diagnostics");

        SourceRef where = section.heading().raw().where();
        Matcher matcher = DECLARATION.matcher(section.name());
        if (!matcher.matches()) {
            error(diagnostics, "invalid scenario declaration '## " + section.name()
                    + "'; expected '## Scenario <title>'", where);
            return Optional.empty();
        }
        String title = matcher.group(1);

        boolean valid = true;
        Section given = null;
        Section when = null;
        Section then = null;
        Set<String> seen = new HashSet<>();
        for (Section subsection : Sections.at(section.content(), 3)) {
            if (!SUBSECTIONS.contains(subsection.name())) {
                error(diagnostics, "unknown subsection '### " + subsection.name()
                        + "' in scenario " + title, subsection.heading().raw().where());
                valid = false;
                continue;
            }
            if (!seen.add(subsection.name())) {
                error(diagnostics, "scenario " + title + " repeats '### " + subsection.name() + "'",
                        subsection.heading().raw().where());
                valid = false;
                continue;
            }
            switch (subsection.name()) {
                case "Given" -> given = subsection;
                case "When" -> when = subsection;
                default -> then = subsection;
            }
        }
        for (String required : List.of("Given", "When", "Then")) {
            if (!seen.contains(required)) {
                error(diagnostics, "scenario " + title + " is missing '### " + required + "'",
                        where);
                valid = false;
            }
        }
        if (!valid) {
            return Optional.empty();
        }

        Optional<Binding> computation = computation(title, when, diagnostics);
        Optional<List<LogicAst.Binding>> bindings = bindings(title, given, false, diagnostics);
        Optional<List<LogicAst.Binding>> expected = bindings(title, then, true, diagnostics);
        if (computation.isEmpty() || bindings.isEmpty() || expected.isEmpty()) {
            return Optional.empty();
        }

        List<LogicAst.Binding> results = expected.orElseThrow();
        if (results.size() != 1 || !results.getFirst().name().equals(RESULT)) {
            error(diagnostics, "'### Then' of scenario " + title
                    + " must contain exactly one '- result: <value>'", where);
            return Optional.empty();
        }
        return Optional.of(new LogicAst.Scenario(
                title,
                computation.orElseThrow().name(),
                computation.orElseThrow().where(),
                bindings.orElseThrow(),
                results.getFirst(),
                where));
    }

    private static Optional<Binding> computation(
            String title, Section when, DiagnosticCollector diagnostics) {
        List<BlockNode> values = semanticContent(when);
        if (values.size() != 1 || values.getFirst().kind() != BlockNode.Kind.PARAGRAPH) {
            error(diagnostics, "'### When' of scenario " + title
                    + " must name exactly one computation", when.heading().raw().where());
            return Optional.empty();
        }
        String name = values.getFirst().raw().text().strip();
        if (!COMPUTATION.matcher(name).matches()) {
            error(diagnostics, "'" + name + "' is not a computation name; expected PascalCase",
                    values.getFirst().raw().where());
            return Optional.empty();
        }
        return Optional.of(new Binding(name, values.getFirst().raw().where()));
    }

    private static Optional<List<LogicAst.Binding>> bindings(
            String title, Section section, boolean expectation, DiagnosticCollector diagnostics) {
        List<LogicAst.Binding> bindings = new ArrayList<>();
        boolean valid = true;
        for (BlockNode block : semanticContent(section)) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                error(diagnostics, "scenario " + title + " accepts only direct list items in '### "
                        + section.name() + "'", block.raw().where());
                valid = false;
                continue;
            }
            Matcher matcher = BINDING.matcher(block.raw().text());
            if (!matcher.matches()) {
                error(diagnostics, "invalid scenario value '" + block.raw().text().strip()
                        + "'; expected '- name: <literal>'", block.raw().where());
                valid = false;
                continue;
            }
            bindings.add(new LogicAst.Binding(
                    matcher.group(1), matcher.group(2), block.raw().where()));
        }
        if (valid && bindings.isEmpty() && expectation) {
            error(diagnostics, "'### Then' of scenario " + title + " must declare a result",
                    section.heading().raw().where());
            valid = false;
        }
        return valid ? Optional.of(bindings) : Optional.empty();
    }

    private static List<BlockNode> semanticContent(Section section) {
        return section.content().stream()
                .filter(block -> block.kind() != BlockNode.Kind.PARAGRAPH
                        || !block.raw().text().isBlank())
                .toList();
    }

    private static void error(
            DiagnosticCollector diagnostics, String message, SourceRef where) {
        diagnostics.error(ErrorCodes.SYNTAX_SCENARIO_SECTION, message, where);
    }

    private record Binding(String name, SourceRef where) {
    }
}
