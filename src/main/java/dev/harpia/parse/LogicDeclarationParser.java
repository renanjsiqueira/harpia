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
 * Parses one {@code ## Logic Name} declaration.
 *
 * <p>The executable block is found by its info string rather than by its position: an executable
 * block belongs to the declaration whose kind matches its fence name. That keeps the canonical
 * source shape identical to the one people and agents already write.
 */
public final class LogicDeclarationParser {

    public static final String PREFIX = "Logic";
    private static final Pattern DECLARATION = Pattern.compile("^Logic +([A-Z][A-Za-z0-9]*)$");
    private static final Pattern PARAMETER =
            Pattern.compile("^\\s*[-*+] +([a-z][A-Za-z0-9]*): +([A-Za-z][A-Za-z0-9]*)\\s*$");
    private static final Set<String> SUBSECTIONS = Set.of("Input", "Output");

    private LogicDeclarationParser() {
    }

    /** True when this H2 heading declares a Logic, regardless of whether it is well formed. */
    public static boolean declares(String heading) {
        return heading.equals(PREFIX) || heading.startsWith(PREFIX + " ");
    }

    public static Optional<LogicAst.Declaration> parse(
            Section section, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(diagnostics, "diagnostics");

        SourceRef where = section.heading().raw().where();
        Matcher matcher = DECLARATION.matcher(section.name());
        if (!matcher.matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_LOGIC_SECTION,
                    "invalid logic declaration '## " + section.name()
                            + "'; expected '## Logic Name' with a PascalCase name",
                    where);
            return Optional.empty();
        }
        String name = matcher.group(1);

        boolean valid = true;
        List<Section> subsections = Sections.at(section.content(), 3);
        Section input = null;
        Section output = null;
        Set<String> seen = new HashSet<>();
        for (Section subsection : subsections) {
            if (!SUBSECTIONS.contains(subsection.name())) {
                error(diagnostics, "unknown subsection '### " + subsection.name() + "' in Logic "
                        + name, subsection.heading().raw().where());
                valid = false;
                continue;
            }
            if (!seen.add(subsection.name())) {
                error(diagnostics, "Logic " + name + " repeats '### " + subsection.name() + "'",
                        subsection.heading().raw().where());
                valid = false;
                continue;
            }
            if (subsection.name().equals("Input")) {
                input = subsection;
            } else {
                output = subsection;
            }
        }
        for (String required : List.of("Input", "Output")) {
            if (!seen.contains(required)) {
                error(diagnostics,
                        "Logic " + name + " is missing '### " + required + "'", where);
                valid = false;
            }
        }

        List<BlockNode> blocks = section.content().stream()
                .filter(LogicDeclarationParser::isLogicBlock)
                .toList();
        if (blocks.size() != 1) {
            error(diagnostics,
                    "Logic " + name + " must contain exactly one fenced code block named 'logic'"
                            + " but found " + blocks.size(),
                    where);
            valid = false;
        }

        Optional<List<LogicAst.Parameter>> parameters = input == null
                ? Optional.empty()
                : parameters(name, input, diagnostics);
        Optional<TypeReference> returnType = output == null
                ? Optional.empty()
                : returnType(name, output, diagnostics);
        Optional<List<LogicAst.Statement>> body = blocks.size() != 1
                ? Optional.empty()
                : LogicBlockParser.parse(
                        blocks.getFirst().fencedBodyLines(),
                        blocks.getFirst().raw().where(),
                        diagnostics);

        valid &= parameters.isPresent() && returnType.isPresent() && body.isPresent();
        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new LogicAst.Declaration(
                name,
                parameters.orElseThrow(),
                returnType.orElseThrow().name(),
                returnType.orElseThrow().where(),
                body.orElseThrow(),
                where));
    }

    private static Optional<List<LogicAst.Parameter>> parameters(
            String logic, Section section, DiagnosticCollector diagnostics) {
        List<LogicAst.Parameter> parameters = new ArrayList<>();
        boolean valid = true;
        for (BlockNode block : semanticContent(section)) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                error(diagnostics,
                        "'### Input' of Logic " + logic + " accepts only direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            Matcher matcher = PARAMETER.matcher(block.raw().text());
            if (!matcher.matches()) {
                error(diagnostics,
                        "invalid Logic parameter '" + block.raw().text().strip()
                                + "'; expected '- name: Type' without modifiers",
                        block.raw().where());
                valid = false;
                continue;
            }
            String type = matcher.group(2);
            if (!FieldLineParser.knownType(type)) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_UNKNOWN_TYPE,
                        "unknown type '" + type + "' in Logic " + logic,
                        LineSyntax.at(block.raw().where(), block.raw().text(), matcher.start(2)));
                valid = false;
                continue;
            }
            parameters.add(new LogicAst.Parameter(
                    matcher.group(1), type, block.raw().where()));
        }
        return valid ? Optional.of(parameters) : Optional.empty();
    }

    private static Optional<TypeReference> returnType(
            String logic, Section section, DiagnosticCollector diagnostics) {
        List<BlockNode> values = semanticContent(section);
        if (values.size() != 1 || values.getFirst().kind() != BlockNode.Kind.PARAGRAPH) {
            error(diagnostics,
                    "'### Output' of Logic " + logic + " must contain exactly one type",
                    section.heading().raw().where());
            return Optional.empty();
        }
        String type = values.getFirst().raw().text().strip();
        if (!FieldLineParser.knownType(type)) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_UNKNOWN_TYPE,
                    "unknown output type '" + type + "' in Logic " + logic,
                    values.getFirst().raw().where());
            return Optional.empty();
        }
        return Optional.of(new TypeReference(type, values.getFirst().raw().where()));
    }

    private static List<BlockNode> semanticContent(Section section) {
        return section.content().stream()
                .filter(block -> !isLogicBlock(block))
                .filter(block -> block.kind() != BlockNode.Kind.PARAGRAPH
                        || !block.raw().text().isBlank())
                .toList();
    }

    private static boolean isLogicBlock(BlockNode block) {
        return block.kind() == BlockNode.Kind.FENCED_CODE && block.info().equals("logic");
    }

    private static void error(
            DiagnosticCollector diagnostics, String message, SourceRef where) {
        diagnostics.error(ErrorCodes.SYNTAX_LOGIC_SECTION, message, where);
    }

    private record TypeReference(String name, SourceRef where) {
    }
}
