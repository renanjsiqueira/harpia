package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.parse.Sections.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parses the implicit V0 entity declared by {@code ## Data}. */
final class DataDeclarationParser implements DeclarationParser {

    @Override
    public DeclarationKind kind() {
        return DeclarationKind.ENTITY;
    }

    @Override
    public boolean recognizes(String heading) {
        return heading.equals("Data");
    }

    @Override
    public Optional<DeclarationAst> parse(
            String moduleName, Section section, DiagnosticCollector diagnostics) {
        List<SpecAst.FieldDeclaration> fields = new ArrayList<>();
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
            Optional<SpecAst.FieldDeclaration> field = FieldLineParser.parseData(
                    block.raw().text(), block.raw().where(), diagnostics);
            if (field.isPresent()) {
                fields.add(field.orElseThrow());
            } else {
                valid = false;
            }
        }
        return valid
                ? Optional.of(new SpecAst.EntityDeclaration(
                        moduleName, fields, section.heading().raw().where()))
                : Optional.empty();
    }
}
