package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.ast.RawSpan;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.Sections.Section;
import dev.harpia.parse.SpecAst.InputDeclaration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses the value contract nested below one integration operation. */
final class IntegrationOperationParser {

    private static final Set<String> SECTIONS = Set.of("Input", "Output", "Errors");
    private static final Pattern ERROR_NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

    private IntegrationOperationParser() {
    }

    static Optional<IntegrationAst.Operation> parse(
            String name, Section operation, DiagnosticCollector diagnostics) {
        List<Section> sections = Sections.at(operation.content(), 4);
        Map<String, List<Section>> byName = new LinkedHashMap<>();
        boolean valid = true;
        for (Section section : sections) {
            if (!SECTIONS.contains(section.name())) {
                error(
                        diagnostics,
                        "unknown operation subsection '#### " + section.name() + "'",
                        section.heading().raw().where());
                valid = false;
                continue;
            }
            byName.computeIfAbsent(section.name(), ignored -> new ArrayList<>()).add(section);
        }
        if (!byName.containsKey("Output")) {
            error(
                    diagnostics,
                    "integration operation '" + name + "' is missing '#### Output'",
                    operation.heading().raw().where());
            valid = false;
        }
        for (Map.Entry<String, List<Section>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                error(
                        diagnostics,
                        "integration operation '" + name + "' repeats '#### "
                                + entry.getKey() + "'",
                        entry.getValue().get(1).heading().raw().where());
                valid = false;
            }
        }

        Parsed<InputDeclaration> input = parseInput(first(byName, "Input"), diagnostics);
        Optional<IntegrationAst.Output> output = parseOutput(
                first(byName, "Output"), diagnostics);
        Parsed<IntegrationAst.Failure> errors = parseErrors(
                first(byName, "Errors"), diagnostics);
        valid &= input.valid() && output.isPresent() && errors.valid();
        return valid
                ? Optional.of(new IntegrationAst.Operation(
                        name,
                        input.items(),
                        output.orElseThrow(),
                        errors.items(),
                        operation.heading().raw().where()))
                : Optional.empty();
    }

    private static Parsed<InputDeclaration> parseInput(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return new Parsed<>(List.of(), true);
        }
        List<InputDeclaration> input = new ArrayList<>();
        boolean valid = true;
        for (BlockNode block : semanticContent(section.orElseThrow())) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                error(diagnostics, "'#### Input' accepts only direct list items", block.raw().where());
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
        return new Parsed<>(input, valid);
    }

    private static Optional<IntegrationAst.Output> parseOutput(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        Optional<RawSpan> value = singleParagraph(section, "Output", diagnostics);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        String type = value.orElseThrow().text().strip();
        if (!type.equals("nothing") && !FieldLineParser.knownType(type)) {
            error(
                    diagnostics,
                    "invalid integration output '" + type
                            + "'; expected nothing, a scalar or a declared type",
                    value.orElseThrow().where());
            return Optional.empty();
        }
        return Optional.of(new IntegrationAst.Output(type, value.orElseThrow().where()));
    }

    private static Parsed<IntegrationAst.Failure> parseErrors(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return new Parsed<>(List.of(), true);
        }
        List<IntegrationAst.Failure> failures = new ArrayList<>();
        Map<String, SourceRef> seen = new LinkedHashMap<>();
        boolean valid = true;
        for (BlockNode block : semanticContent(section.orElseThrow())) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                error(diagnostics, "'#### Errors' accepts only direct list items", block.raw().where());
                valid = false;
                continue;
            }
            String name = block.listItemText().strip();
            if (!ERROR_NAME.matcher(name).matches()) {
                error(
                        diagnostics,
                        "invalid integration error '" + name + "'; expected a PascalCase name",
                        block.raw().where());
                valid = false;
                continue;
            }
            SourceRef first = seen.putIfAbsent(name, block.raw().where());
            if (first != null) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_INTEGRATION,
                        "integration operation repeats error '" + name + "'",
                        block.raw().where(),
                        "first declared here",
                        first);
                valid = false;
                continue;
            }
            failures.add(new IntegrationAst.Failure(name, block.raw().where()));
        }
        return new Parsed<>(failures, valid);
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
                    "'#### " + name + "' must contain exactly one text line",
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
        diagnostics.error(ErrorCodes.SYNTAX_INTEGRATION, message, where);
    }

    private record Parsed<T>(List<T> items, boolean valid) {
        private Parsed {
            items = List.copyOf(items);
        }
    }
}
