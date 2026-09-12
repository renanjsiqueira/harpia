package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.parse.Sections.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code ## Invariants}: conditions the entity satisfies whenever it is stored.
 *
 * <p>A rule constrains what one operation was asked to do. An invariant constrains what the entity
 * is allowed to be, so it holds no matter which operation ran.
 */
final class InvariantDeclarationParser implements DeclarationParser {

    private static final String HEADING = "Invariants";

    @Override
    public DeclarationKind kind() {
        return DeclarationKind.INVARIANT;
    }

    @Override
    public boolean recognizes(String heading) {
        return heading.equals(HEADING);
    }

    @Override
    public Optional<DeclarationAst> parse(
            String moduleName, Section section, DiagnosticCollector diagnostics) {
        List<SpecAst.RuleDeclaration> conditions = new ArrayList<>();
        boolean valid = true;
        boolean declaresAnything = false;
        for (BlockNode block : section.content()) {
            if (block.kind() != BlockNode.Kind.LIST_ITEM) {
                continue;
            }
            declaresAnything = true;
            if (block.listDepth() != 1) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_INVARIANT,
                        "invariants must be direct list items",
                        block.raw().where());
                valid = false;
                continue;
            }
            String text = block.listItemText().strip();
            Optional<LogicAst.Expression> condition =
                    LogicLexer.tokenize(text, block.raw().where(), diagnostics)
                            .flatMap(tokens -> LogicExpressionParser.parse(tokens, diagnostics));
            if (condition.isPresent()) {
                conditions.add(new SpecAst.RuleDeclaration(
                        text, condition.orElseThrow(), block.raw().where()));
            } else {
                valid = false;
            }
        }
        if (conditions.isEmpty() && !declaresAnything) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_INVARIANT,
                    "'## Invariants' declares no conditions",
                    section.heading().raw().where());
            valid = false;
        }
        return valid
                ? Optional.of(new SpecAst.InvariantDeclaration(
                        moduleName, conditions, section.heading().raw().where()))
                : Optional.empty();
    }
}
