package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.ast.RawSpan;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.Sections.Section;
import dev.harpia.parse.SpecAst.Access;
import dev.harpia.parse.SpecAst.Endpoint;
import dev.harpia.parse.SpecAst.ErrorDeclaration;
import dev.harpia.parse.SpecAst.FlowStatement;
import dev.harpia.parse.SpecAst.InputDeclaration;
import dev.harpia.parse.SpecAst.Output;
import dev.harpia.parse.SpecAst.UseCaseDeclaration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parser for an application operation.
 *
 * <p>The three syntaxes share every section, so they share this parser and differ only in what the
 * heading declares. {@code ## Command Checkout} and {@code ## Query GetCustomer} say what the
 * operation is; the unprefixed V0 heading leaves it to be inferred from the shape of the flow, and
 * is kept as the fallback so nothing already written changes meaning.
 */
final class UseCaseDeclarationParser implements DeclarationParser {

    private final DeclarationKind declaredKind;
    private final String prefix;
    private final boolean executableRules;
    private final boolean conditionalFlow;

    /**
     * Prefixes a later language version gives meaning to. The fallback only sees them when the
     * project speaks a version that does not, and reading {@code ## Command Register Customer} as
     * a use case called {@code CommandRegisterCustomer} would be a silent misunderstanding.
     */
    private static final Map<String, String> LATER_KINDS =
            Map.of(
                    "Command", "1",
                    "Query", "1",
                    "Enum", "1",
                    "Value", "1",
                    "Integration", "1",
                    "Event", "1");
    private static final Map<String, String> LATER_SECTIONS = Map.of("Invariants", "1");

    /** The legacy V0 form: any heading, kind inferred later, `### Rules` documentary. */
    UseCaseDeclarationParser() {
        this(DeclarationKind.USE_CASE, null, false, false);
    }

    UseCaseDeclarationParser(DeclarationKind declaredKind, String prefix) {
        this(declaredKind, prefix, true, true);
    }

    /**
     * @param executableRules whether a list item under {@code ### Rules} is a condition to enforce.
     *     In V0 the whole section is prose, and a specification written then must keep meaning what
     *     it meant.
     */
    UseCaseDeclarationParser(
            DeclarationKind declaredKind, String prefix, boolean executableRules) {
        this(declaredKind, prefix, executableRules, executableRules);
    }

    private UseCaseDeclarationParser(
            DeclarationKind declaredKind,
            String prefix,
            boolean executableRules,
            boolean conditionalFlow) {
        this.declaredKind = declaredKind;
        this.prefix = prefix;
        this.executableRules = executableRules;
        this.conditionalFlow = conditionalFlow;
    }

    private static final Pattern TITLE =
            Pattern.compile("[A-Z][A-Za-z0-9]*(?: +[A-Z][A-Za-z0-9]*)*");
    private static final Set<String> SECTIONS = Set.of(
            "Endpoint", "Access", "Input", "Rules", "Flow", "Output", "Errors");
    private static final List<String> REQUIRED_SECTIONS = List.of("Flow", "Output");

    @Override
    public DeclarationKind kind() {
        return declaredKind;
    }

    @Override
    public boolean recognizes(String heading) {
        return prefix == null || heading.startsWith(prefix + " ");
    }

    @Override
    public Optional<DeclarationAst> parse(
            String moduleName, Section useCase, DiagnosticCollector diagnostics) {
        if (UnsupportedFeatureDetector.unsupportedSection(useCase.name())) {
            UnsupportedFeatureDetector.reportSection(
                    useCase.name(), diagnostics, useCase.heading().raw().where());
            return Optional.empty();
        }

        if (prefix == null) {
            for (Map.Entry<String, String> later : new java.util.TreeMap<>(LATER_KINDS).entrySet()) {
                if (useCase.name().startsWith(later.getKey() + " ")) {
                    diagnostics.error(
                            ErrorCodes.SYNTAX_DECLARATION_TOO_NEW,
                            "'## " + useCase.name() + "' declares "
                                    + article(later.getKey()) + " " + later.getKey()
                                    + ", which needs harpia.languageVersion "
                                    + later.getValue(),
                            useCase.heading().raw().where(),
                            "set harpia.languageVersion to " + later.getValue()
                                    + ", or rename the heading");
                    return Optional.empty();
                }
            }
        }
        boolean valid = true;
        String title = prefix == null
                ? useCase.name()
                : useCase.name().substring(prefix.length()).strip();
        if (!TITLE.matcher(title).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_USE_CASE_TITLE,
                    "invalid " + label() + " name '" + title
                            + "'; expected PascalCase words separated by spaces",
                    useCase.heading().raw().where());
            valid = false;
        }

        List<Section> sections = Sections.at(useCase.content(), 3);
        Map<String, List<Section>> byName = new LinkedHashMap<>();
        for (Section section : sections) {
            if (!SECTIONS.contains(section.name())) {
                error(
                        diagnostics,
                        "unknown subsection '### " + section.name() + "'",
                        section.heading().raw().where());
                valid = false;
                continue;
            }
            byName.computeIfAbsent(section.name(), ignored -> new ArrayList<>()).add(section);
        }

        for (String required : REQUIRED_SECTIONS) {
            if (!byName.containsKey(required)) {
                error(
                        diagnostics,
                        label() + " '" + title + "' is missing '### " + required + "'",
                        useCase.heading().raw().where());
                valid = false;
            }
        }
        if (declaredKind == DeclarationKind.USE_CASE) {
            for (String required : List.of("Endpoint", "Access")) {
                if (!byName.containsKey(required)) {
                    error(
                            diagnostics,
                            label() + " '" + title + "' is missing '### " + required + "'",
                            useCase.heading().raw().where());
                    valid = false;
                }
            }
        } else if (byName.containsKey("Endpoint") != byName.containsKey("Access")) {
            String missing = byName.containsKey("Endpoint") ? "Access" : "Endpoint";
            error(
                    diagnostics,
                    label() + " '" + title + "' declares an inline HTTP binding but is missing "
                            + "'### " + missing + "'",
                    useCase.heading().raw().where());
            valid = false;
        }
        for (Map.Entry<String, List<Section>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                error(
                        diagnostics,
                        label() + " '" + title + "' repeats '### " + entry.getKey() + "'",
                        entry.getValue().get(1).heading().raw().where());
                valid = false;
            }
        }

        Optional<Endpoint> endpoint = parseEndpoint(first(byName, "Endpoint"), diagnostics);
        Optional<Access> access = parseAccess(first(byName, "Access"), diagnostics);
        ParseItems<InputDeclaration> input = parseInput(first(byName, "Input"), diagnostics);
        ParseItems<SpecAst.RuleDeclaration> rules = parseRules(first(byName, "Rules"), diagnostics);
        ParseItems<FlowStatement> flow = parseFlow(first(byName, "Flow"), diagnostics);
        Optional<Output> output = parseOutput(first(byName, "Output"), diagnostics);
        ParseItems<ErrorDeclaration> errors = parseErrors(first(byName, "Errors"), diagnostics);

        if (byName.containsKey("Endpoint")) {
            valid &= endpoint.isPresent();
        }
        if (byName.containsKey("Access")) {
            valid &= access.isPresent();
        }
        valid &= input.valid();
        valid &= rules.valid();
        valid &= flow.valid();
        valid &= output.isPresent();
        valid &= errors.valid();
        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new UseCaseDeclaration(
                declaredKind,
                title,
                endpoint,
                access,
                input.items(),
                rules.items(),
                flow.items(),
                output.orElseThrow(),
                errors.items(),
                useCase.heading().raw().where()));
    }

    private String label() {
        return declaredKind == DeclarationKind.USE_CASE
                ? "use-case"
                : declaredKind.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Optional<Endpoint> parseEndpoint(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        Optional<RawSpan> value = singleParagraph(section, "Endpoint", diagnostics);
        return value.flatMap(raw -> EndpointParser.parse(raw.text(), raw.where(), diagnostics));
    }

    private static Optional<Access> parseAccess(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return Optional.empty();
        }
        List<BlockNode> values = semanticContent(section.orElseThrow());
        List<BlockNode> listItems = values.stream()
                .filter(block -> block.kind() == BlockNode.Kind.LIST_ITEM && block.listDepth() == 1)
                .toList();
        if (!listItems.isEmpty() && listItems.size() == values.size()) {
            String access = listItems.stream()
                    .map(BlockNode::listItemText)
                    .map(String::strip)
                    .toList()
                    .toString();
            UnsupportedFeatureDetector.reportAccess(
                    access, diagnostics, listItems.getFirst().raw().where());
            return Optional.empty();
        }
        Optional<RawSpan> value = singleParagraph(section, "Access", diagnostics);
        return value.flatMap(raw -> AccessParser.parse(raw.text(), raw.where(), diagnostics));
    }

    private static ParseItems<InputDeclaration> parseInput(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return new ParseItems<>(List.of(), true);
        }
        List<InputDeclaration> input = new ArrayList<>();
        boolean valid = true;
        for (BlockNode block : semanticContent(section.orElseThrow())) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                error(
                        diagnostics,
                        "'### Input' accepts only direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            Optional<InputDeclaration> declaration = FieldLineParser.parseInput(
                    block.raw().text(), block.raw().where(), diagnostics);
            if (declaration.isPresent()) {
                input.add(declaration.orElseThrow());
            } else {
                valid = false;
            }
        }
        return new ParseItems<>(input, valid);
    }

    private ParseItems<FlowStatement> parseFlow(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return new ParseItems<>(List.of(), false);
        }
        List<BlockNode> values = semanticContent(section.orElseThrow());
        if (values.size() != 1
                || values.getFirst().kind() != BlockNode.Kind.FENCED_CODE
                || !values.getFirst().info().equals("flow")) {
            error(
                    diagnostics,
                    "'### Flow' must contain exactly one fenced code block named 'flow'",
                    section.orElseThrow().heading().raw().where());
            return new ParseItems<>(List.of(), false);
        }

        Optional<List<FlowStatement>> flow = FlowBlockParser.parse(
                values.getFirst().fencedBodyLines(),
                values.getFirst().raw().where(),
                conditionalFlow,
                diagnostics);
        return new ParseItems<>(flow.orElse(List.of()), flow.isPresent());
    }

    private static Optional<Output> parseOutput(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        Optional<RawSpan> value = singleParagraph(section, "Output", diagnostics);
        return value.flatMap(raw -> OutputParser.parse(raw.text(), raw.where(), diagnostics));
    }

    private ParseItems<SpecAst.RuleDeclaration> parseRules(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty() || !executableRules) {
            return new ParseItems<>(List.of(), true);
        }
        List<SpecAst.RuleDeclaration> rules = new ArrayList<>();
        boolean valid = true;
        for (BlockNode block : semanticContent(section.orElseThrow())) {
            // Prose under `### Rules` stays documentation. A rule is a list item, the same shape
            // every other enforceable section of a declaration uses.
            if (block.kind() != BlockNode.Kind.LIST_ITEM) {
                continue;
            }
            if (block.listDepth() != 1) {
                error(
                        diagnostics,
                        "'### Rules' accepts only direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            String text = block.listItemText().strip();
            Optional<LogicAst.Expression> condition =
                    LogicLexer.tokenize(text, block.raw().where(), diagnostics)
                            .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
            if (condition.isPresent()) {
                rules.add(new SpecAst.RuleDeclaration(
                        text, condition.orElseThrow(), block.raw().where()));
            } else {
                valid = false;
            }
        }
        return new ParseItems<>(rules, valid);
    }

    private static ParseItems<ErrorDeclaration> parseErrors(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return new ParseItems<>(List.of(), true);
        }
        List<ErrorDeclaration> errors = new ArrayList<>();
        boolean valid = true;
        for (BlockNode block : semanticContent(section.orElseThrow())) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                error(
                        diagnostics,
                        "'### Errors' accepts only direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            Optional<ErrorDeclaration> declaration = ErrorLineParser.parse(
                    block.raw().text(), block.raw().where(), diagnostics);
            if (declaration.isPresent()) {
                errors.add(declaration.orElseThrow());
            } else {
                valid = false;
            }
        }
        return new ParseItems<>(errors, valid);
    }

    private static Optional<RawSpan> singleParagraph(
            Optional<Section> section,
            String name,
            DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return Optional.empty();
        }
        List<BlockNode> values = semanticContent(section.orElseThrow());
        if (values.size() != 1 || values.getFirst().kind() != BlockNode.Kind.PARAGRAPH) {
            error(
                    diagnostics,
                    "'### " + name + "' must contain exactly one text line",
                    section.orElseThrow().heading().raw().where());
            return Optional.empty();
        }
        return Optional.of(values.getFirst().raw());
    }

    private static List<BlockNode> semanticContent(Section section) {
        return section.content().stream()
                .filter(block -> block.kind() != BlockNode.Kind.PARAGRAPH
                        || !block.raw().text().isBlank())
                .toList();
    }

    private static Optional<Section> first(
            Map<String, List<Section>> sections, String name) {
        List<Section> values = sections.get(name);
        return values == null || values.isEmpty()
                ? Optional.empty()
                : Optional.of(values.getFirst());
    }

    private static void error(
            DiagnosticCollector diagnostics, String message, SourceRef where) {
        diagnostics.error(ErrorCodes.SYNTAX_USE_CASE_SECTION, message, where);
    }

    private record ParseItems<T>(List<T> items, boolean valid) {
        private ParseItems {
            items = List.copyOf(items);
        }
    }

    /** Enum, Event and Integration all start with a vowel, and "a Enum" reads like a typo. */
    private static String article(String kind) {
        return "AEIOU".indexOf(kind.charAt(0)) >= 0 ? "an" : "a";
    }
}
