package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.parse.Sections.Section;
import dev.harpia.parse.SpecAst.InputDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parses a domain event declared by {@code ## Event <Name>}. */
final class EventDeclarationParser implements DeclarationParser {

    private static final String PREFIX = "Event ";
    private static final String PAYLOAD = "Payload";
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

    @Override
    public DeclarationKind kind() {
        return DeclarationKind.EVENT;
    }

    @Override
    public boolean recognizes(String heading) {
        return heading.equals("Event") || heading.startsWith(PREFIX);
    }

    @Override
    public Optional<DeclarationAst> parse(
            String moduleName, Section section, DiagnosticCollector diagnostics) {
        String heading = section.heading().headingText();
        String name = heading.startsWith(PREFIX) ? heading.substring(PREFIX.length()).strip() : "";
        boolean valid = true;
        if (!NAME.matcher(name).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_EVENT,
                    "invalid event name '" + name + "'; expected '## Event <PascalCaseName>'",
                    section.heading().raw().where());
            valid = false;
        }

        List<Section> sections = Sections.at(section.content(), 3);
        Optional<Section> payload = Optional.empty();
        for (Section nested : sections) {
            if (!nested.name().equals(PAYLOAD)) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_EVENT,
                        "unknown event subsection '### " + nested.name()
                                + "'; an event declares only '### Payload'",
                        nested.heading().raw().where());
                valid = false;
                continue;
            }
            if (payload.isPresent()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_EVENT,
                        "event '" + name + "' repeats '### Payload'",
                        nested.heading().raw().where());
                valid = false;
                continue;
            }
            payload = Optional.of(nested);
        }
        if (payload.isEmpty() && valid) {
            // An event with nothing in it announces that something happened without saying what
            // it happened to, which no subscriber can act on.
            diagnostics.error(
                    ErrorCodes.SYNTAX_EVENT,
                    "event '" + name + "' is missing '### Payload'",
                    section.heading().raw().where());
            valid = false;
        }

        List<InputDeclaration> fields = new ArrayList<>();
        if (payload.isPresent()) {
            for (BlockNode block : payload.orElseThrow().content()) {
                if (block.kind() == BlockNode.Kind.PARAGRAPH && block.raw().text().isBlank()) {
                    continue;
                }
                if (block.kind() != BlockNode.Kind.LIST_ITEM || block.listDepth() != 1) {
                    diagnostics.error(
                            ErrorCodes.SYNTAX_EVENT,
                            "'### Payload' accepts only direct list items",
                            block.raw().where());
                    valid = false;
                    continue;
                }
                Optional<InputDeclaration> field = FieldLineParser.parseInput(
                        block.raw().text(), block.raw().where(), diagnostics);
                field.ifPresent(fields::add);
                valid &= field.isPresent();
            }
            if (fields.isEmpty() && valid) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_EVENT,
                        "event '" + name + "' declares an empty '### Payload'",
                        payload.orElseThrow().heading().raw().where());
                valid = false;
            }
        }

        return valid
                ? Optional.of(new EventAst.Declaration(
                        name, fields, section.heading().raw().where()))
                : Optional.empty();
    }
}
