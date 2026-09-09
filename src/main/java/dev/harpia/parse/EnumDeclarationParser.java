package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.parse.Sections.Section;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses {@code ## Enum <Name>}: a closed set of values the project names itself.
 *
 * <p>The values are written in the specification's own vocabulary, not a target language's. What a
 * target calls them is the target's business.
 */
final class EnumDeclarationParser implements DeclarationParser {

    private static final String PREFIX = "Enum ";
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private static final Pattern VALUE = Pattern.compile("[a-z][a-z0-9]*(?:_[a-z0-9]+)*");

    @Override
    public DeclarationKind kind() {
        return DeclarationKind.ENUM;
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
                    ErrorCodes.SYNTAX_ENUM_VALUE,
                    "invalid enum name '" + name + "'; expected '## Enum <PascalCaseName>'",
                    section.heading().raw().where());
            valid = false;
        }

        List<SpecAst.EnumValue> values = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean declaresAnything = false;
        for (BlockNode block : section.content()) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM) {
                continue;
            }
            declaresAnything = true;
            if (block.listDepth() != 1) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_ENUM_VALUE,
                        "enum values must be direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            String value = block.listItemText().strip();
            if (!VALUE.matcher(value).matches()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_ENUM_VALUE,
                        "invalid enum value '" + value
                                + "'; expected lower_snake_case such as 'awaiting_payment'",
                        block.raw().where());
                valid = false;
                continue;
            }
            if (!seen.add(value)) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_ENUM_VALUE,
                        "duplicate enum value '" + value + "'",
                        block.raw().where());
                valid = false;
                continue;
            }
            values.add(new SpecAst.EnumValue(value, block.raw().where()));
        }
        // An item that failed to parse was already reported. Saying the enum declares nothing on
        // top of that describes the consequence of the first error, not a second problem.
        if (values.isEmpty() && !declaresAnything) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ENUM_VALUE,
                    "enum '" + name + "' declares no values",
                    section.heading().raw().where());
            valid = false;
        }
        return valid
                ? Optional.of(new SpecAst.EnumDeclaration(
                        name, values, section.heading().raw().where()))
                : Optional.empty();
    }
}
