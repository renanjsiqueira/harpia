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

/** Parses an outbound port declared by {@code ## Integration <Name>}. */
final class IntegrationDeclarationParser implements DeclarationParser {

    private static final String PREFIX = "Integration ";
    private static final String OPERATION_PREFIX = "Operation ";
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

    @Override
    public DeclarationKind kind() {
        return DeclarationKind.INTEGRATION;
    }

    @Override
    public boolean recognizes(String heading) {
        return heading.equals("Integration") || heading.startsWith(PREFIX);
    }

    @Override
    public Optional<DeclarationAst> parse(
            String moduleName, Section section, DiagnosticCollector diagnostics) {
        String heading = section.heading().headingText();
        String name = heading.startsWith(PREFIX)
                ? heading.substring(PREFIX.length()).strip()
                : "";
        boolean valid = true;
        if (!NAME.matcher(name).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_INTEGRATION,
                    "invalid integration name '" + name
                            + "'; expected '## Integration <PascalCaseName>'",
                    section.heading().raw().where());
            valid = false;
        }

        List<IntegrationAst.Operation> operations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Section operation : Sections.at(section.content(), 3)) {
            String operationHeading = operation.name();
            String operationName = operationHeading.startsWith(OPERATION_PREFIX)
                    ? operationHeading.substring(OPERATION_PREFIX.length()).strip()
                    : "";
            if (!operationHeading.startsWith(OPERATION_PREFIX)
                    || !NAME.matcher(operationName).matches()) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_INTEGRATION,
                        "invalid integration operation '### " + operationHeading
                                + "'; expected '### Operation <PascalCaseName>'",
                        operation.heading().raw().where());
                valid = false;
                continue;
            }
            if (!seen.add(operationName)) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_INTEGRATION,
                        "integration '" + name + "' repeats operation '" + operationName + "'",
                        operation.heading().raw().where());
                valid = false;
                continue;
            }
            boolean contract = operation.content().stream()
                    .anyMatch(block -> block.kind() == BlockNode.Kind.HEADING
                            && block.headingLevel() >= 4);
            if (contract) {
                diagnostics.error(
                        ErrorCodes.SYNTAX_DECLARATION_TOO_NEW,
                        "typed integration operation contracts are implemented by INTEG-002",
                        operation.heading().raw().where());
                valid = false;
                continue;
            }
            operations.add(new IntegrationAst.Operation(
                    operationName, operation.heading().raw().where()));
        }
        if (operations.isEmpty() && valid) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_INTEGRATION,
                    "integration '" + name
                            + "' declares no operations; add '### Operation <Name>'",
                    section.heading().raw().where());
            valid = false;
        }
        return valid
                ? Optional.of(new IntegrationAst.Declaration(
                        name, operations, section.heading().raw().where()))
                : Optional.empty();
    }
}
