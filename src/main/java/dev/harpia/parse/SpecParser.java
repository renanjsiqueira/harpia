package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.ast.MarkdownStructure;
import dev.harpia.ast.RawSpan;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.Access;
import dev.harpia.parse.SpecAst.Endpoint;
import dev.harpia.parse.SpecAst.ErrorDeclaration;
import dev.harpia.parse.SpecAst.FieldDeclaration;
import dev.harpia.parse.SpecAst.FlowStatement;
import dev.harpia.parse.SpecAst.InputDeclaration;
import dev.harpia.parse.SpecAst.Output;
import dev.harpia.parse.SpecAst.UseCaseDeclaration;
import dev.harpia.parse.Sections.Section;
import dev.harpia.source.SourceFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Builds the Harpia syntax tree from the structural CommonMark view. */
public final class SpecParser {

    private static final Pattern ENTITY_NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private static final Pattern USE_CASE_TITLE =
            Pattern.compile("[A-Z][A-Za-z0-9]*(?: +[A-Z][A-Za-z0-9]*)*");
    private static final Set<String> USE_CASE_SECTIONS = Set.of(
            "Endpoint", "Access", "Input", "Rules", "Flow", "Output", "Errors");
    private static final List<String> REQUIRED_USE_CASE_SECTIONS =
            List.of("Endpoint", "Access", "Flow", "Output");

    private SpecParser() {
    }

    public static Optional<SpecAst> parse(
            SourceFile source, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(source, "source");
        return parse(MarkdownStructure.parse(source), diagnostics);
    }

    public static Optional<SpecAst> parse(
            MarkdownStructure markdown, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<BlockNode> blocks = markdown.blocks();
        List<BlockNode> entityHeadings = blocks.stream()
                .filter(block -> block.kind() == BlockNode.Kind.HEADING)
                .filter(block -> block.headingLevel() == 1)
                .toList();

        boolean valid = true;
        if (entityHeadings.size() != 1) {
            SourceRef where = entityHeadings.size() > 1
                    ? entityHeadings.get(1).raw().where()
                    : SourceRef.file(markdown.source().relativePath());
            diagnostics.error(
                    ErrorCodes.SYNTAX_H1,
                    "expected exactly one entity H1 but found " + entityHeadings.size(),
                    where);
            valid = false;
        }

        BlockNode entityHeading = entityHeadings.isEmpty() ? null : entityHeadings.getFirst();
        String entityName = entityHeading == null ? "Invalid" : entityHeading.headingText();
        SourceRef entityWhere = entityHeading == null
                ? SourceRef.file(markdown.source().relativePath())
                : entityHeading.raw().where();
        if (entityHeading != null && !ENTITY_NAME.matcher(entityName).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ENTITY_NAME,
                    "invalid entity name '" + entityName + "'; expected PascalCase",
                    entityWhere);
            valid = false;
        }

        List<Section> levelTwoSections = Sections.at(blocks, 2);
        List<Section> dataSections = levelTwoSections.stream()
                .filter(section -> section.name().equals("Data"))
                .toList();
        if (dataSections.size() > 1) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DATA_SECTION,
                    "expected at most one '## Data' section but found " + dataSections.size(),
                    dataSections.get(1).heading().raw().where());
            valid = false;
        }
        boolean declaresEntity = !dataSections.isEmpty();

        List<FieldDeclaration> fields = new ArrayList<>();
        for (Section data : dataSections) {
            ParseItems<FieldDeclaration> parsed = parseData(data, diagnostics);
            fields.addAll(parsed.items());
            valid &= parsed.valid();
        }

        List<LogicAst.Declaration> logics = new ArrayList<>();
        List<LogicAst.Scenario> scenarios = new ArrayList<>();
        List<UseCaseDeclaration> useCases = new ArrayList<>();
        boolean declaresMember = false;
        for (Section section : levelTwoSections) {
            if (section.name().equals("Data")) {
                continue;
            }
            declaresMember = true;
            if (ScenarioDeclarationParser.declares(section.name())) {
                Optional<LogicAst.Scenario> scenario =
                        ScenarioDeclarationParser.parse(section, diagnostics);
                if (scenario.isPresent()) {
                    scenarios.add(scenario.orElseThrow());
                } else {
                    valid = false;
                }
                continue;
            }
            if (LogicDeclarationParser.declares(section.name())) {
                Optional<LogicAst.Declaration> logic =
                        LogicDeclarationParser.parse(section, diagnostics);
                if (logic.isPresent()) {
                    logics.add(logic.orElseThrow());
                } else {
                    valid = false;
                }
                continue;
            }
            if (UnsupportedFeatureDetector.unsupportedSection(section.name())) {
                UnsupportedFeatureDetector.reportSection(
                        section.name(), diagnostics, section.heading().raw().where());
                valid = false;
                continue;
            }
            if (!USE_CASE_TITLE.matcher(section.name()).matches()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_USE_CASE_TITLE,
                        "invalid use-case title '" + section.name()
                                + "'; expected PascalCase words separated by spaces",
                        section.heading().raw().where());
                valid = false;
            }

            Optional<UseCaseDeclaration> useCase = parseUseCase(section, diagnostics);
            if (useCase.isPresent()) {
                useCases.add(useCase.orElseThrow());
            } else {
                valid = false;
            }
        }

        if (!declaresEntity && !declaresMember) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DATA_SECTION,
                    "module '" + entityName
                            + "' declares nothing; add '## Data' or at least one declaration",
                    entityWhere);
            valid = false;
        }
        if (!declaresEntity && !useCases.isEmpty()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DATA_SECTION,
                    "module '" + entityName
                            + "' declares use cases and therefore requires a '## Data' section",
                    entityWhere);
            valid = false;
        }

        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new SpecAst(
                markdown.source().relativePath(),
                entityName,
                declaresEntity,
                fields,
                useCases,
                logics,
                scenarios,
                entityWhere));
    }

    private static ParseItems<FieldDeclaration> parseData(
            Section section, DiagnosticCollector diagnostics) {
        List<FieldDeclaration> fields = new ArrayList<>();
        boolean valid = true;
        for (BlockNode block : section.content()) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM) {
                continue;
            }
            if (block.listDepth() != 1) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_FIELD_LINE,
                        "data fields must be direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            Optional<FieldDeclaration> field = FieldLineParser.parseData(
                    block.raw().text(), block.raw().where(), diagnostics);
            if (field.isPresent()) {
                fields.add(field.orElseThrow());
            } else {
                valid = false;
            }
        }
        return new ParseItems<>(fields, valid);
    }

    private static Optional<UseCaseDeclaration> parseUseCase(
            Section useCase, DiagnosticCollector diagnostics) {
        List<Section> sections = Sections.at(useCase.content(), 3);
        Map<String, List<Section>> byName = new LinkedHashMap<>();
        boolean valid = true;

        for (Section section : sections) {
            if (!USE_CASE_SECTIONS.contains(section.name())) {
                useCaseError(
                        diagnostics,
                        "unknown subsection '### " + section.name() + "'",
                        section.heading().raw().where());
                valid = false;
                continue;
            }
            byName.computeIfAbsent(section.name(), ignored -> new ArrayList<>()).add(section);
        }

        for (String required : REQUIRED_USE_CASE_SECTIONS) {
            if (!byName.containsKey(required)) {
                useCaseError(
                        diagnostics,
                        "use case '" + useCase.name() + "' is missing '### " + required + "'",
                        useCase.heading().raw().where());
                valid = false;
            }
        }
        for (Map.Entry<String, List<Section>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                useCaseError(
                        diagnostics,
                        "use case '" + useCase.name() + "' repeats '### " + entry.getKey() + "'",
                        entry.getValue().get(1).heading().raw().where());
                valid = false;
            }
        }

        Optional<Endpoint> endpoint = parseEndpoint(first(byName, "Endpoint"), diagnostics);
        Optional<Access> access = parseAccess(first(byName, "Access"), diagnostics);
        ParseItems<InputDeclaration> input = parseInput(first(byName, "Input"), diagnostics);
        ParseItems<FlowStatement> flow = parseFlow(first(byName, "Flow"), diagnostics);
        Optional<Output> output = parseOutput(first(byName, "Output"), diagnostics);
        ParseItems<ErrorDeclaration> errors = parseErrors(first(byName, "Errors"), diagnostics);

        valid &= endpoint.isPresent();
        valid &= access.isPresent();
        valid &= input.valid();
        valid &= flow.valid();
        valid &= output.isPresent();
        valid &= errors.valid();
        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new UseCaseDeclaration(
                useCase.name(),
                endpoint.orElseThrow(),
                access.orElseThrow(),
                input.items(),
                flow.items(),
                output.orElseThrow(),
                errors.items(),
                useCase.heading().raw().where()));
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
                useCaseError(
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

    private static ParseItems<FlowStatement> parseFlow(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        if (section.isEmpty()) {
            return new ParseItems<>(List.of(), false);
        }
        List<BlockNode> values = semanticContent(section.orElseThrow());
        if (values.size() != 1
                || values.getFirst().kind() != BlockNode.Kind.FENCED_CODE
                || !values.getFirst().info().equals("flow")) {
            useCaseError(
                    diagnostics,
                    "'### Flow' must contain exactly one fenced code block named 'flow'",
                    section.orElseThrow().heading().raw().where());
            return new ParseItems<>(List.of(), false);
        }

        List<FlowStatement> flow = new ArrayList<>();
        boolean valid = true;
        for (RawSpan line : values.getFirst().fencedBodyLines()) {
            if (line.text().isBlank()) {
                continue;
            }
            Optional<FlowStatement> statement =
                    FlowLineParser.parse(line.text(), line.where(), diagnostics);
            if (statement.isPresent()) {
                flow.add(statement.orElseThrow());
            } else {
                valid = false;
            }
        }
        return new ParseItems<>(flow, valid);
    }

    private static Optional<Output> parseOutput(
            Optional<Section> section, DiagnosticCollector diagnostics) {
        Optional<RawSpan> value = singleParagraph(section, "Output", diagnostics);
        return value.flatMap(raw -> OutputParser.parse(raw.text(), raw.where(), diagnostics));
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
                useCaseError(
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
            useCaseError(
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

    private static void useCaseError(
            DiagnosticCollector diagnostics, String message, SourceRef where) {
        diagnostics.error(ErrorCodes.SYNTAX_USE_CASE_SECTION, message, where);
    }

    private record ParseItems<T>(List<T> items, boolean valid) {
        private ParseItems {
            items = List.copyOf(items);
        }
    }
}
