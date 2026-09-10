package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.parse.Sections.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses {@code ## Value <Name>}: a named group of fields with no identity of its own.
 *
 * <p>A value is compared by what it holds, so it declares no id, is never unique on its own and is
 * never generated. Those modifiers belong to something that has identity, which is an entity.
 */
final class ValueDeclarationParser implements DeclarationParser {

    private static final String PREFIX = "Value ";
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

    @Override
    public DeclarationKind kind() {
        return DeclarationKind.VALUE;
    }

    @Override
    public boolean recognizes(String heading) {
        return heading.startsWith(PREFIX);
    }

    @Override
    public Optional<DeclarationAst> parse(
            String moduleName, Section section, DiagnosticCollector diagnostics) {
        String heading = section.heading().headingText();
        String name = heading.substring(PREFIX.length()).strip();
        boolean valid = true;
        if (!NAME.matcher(name).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_VALUE_FIELD,
                    "invalid value name '" + name + "'; expected '## Value <PascalCaseName>'",
                    section.heading().raw().where());
            valid = false;
        }

        List<SpecAst.FieldDeclaration> fields = new ArrayList<>();
        boolean declaresAnything = false;
        for (BlockNode block : section.content()) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM) {
                continue;
            }
            declaresAnything = true;
            if (block.listDepth() != 1) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_VALUE_FIELD,
                        "value fields must be direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            Optional<SpecAst.FieldDeclaration> field = FieldLineParser.parseData(
                    block.raw().text(), block.raw().where(), diagnostics);
            if (field.isEmpty()) {
                valid = false;
                continue;
            }
            SpecAst.FieldDeclaration declared = field.orElseThrow();
            if (declared.generated() || declared.unique() || declared.owned()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_VALUE_FIELD,
                        "field '" + declared.name() + "' of value '" + name
                                + "' cannot be generated or unique, and cannot be owned; a value "
                                + "has no identity or lifecycle",
                        declared.where());
                valid = false;
                continue;
            }
            fields.add(declared);
        }
        if (fields.isEmpty() && !declaresAnything) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_VALUE_FIELD,
                    "value '" + name + "' declares no fields",
                    section.heading().raw().where());
            valid = false;
        }
        return valid
                ? Optional.of(new SpecAst.ValueDeclaration(
                        name, fields, section.heading().raw().where()))
                : Optional.empty();
    }
}
