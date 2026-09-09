package dev.harpia.parse;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.parse.Sections.Section;
import java.util.Optional;

/** Parses one H2 declaration kind into a syntax node. */
interface DeclarationParser {

    DeclarationKind kind();

    boolean recognizes(String heading);

    Optional<DeclarationAst> parse(
            String moduleName, Section section, DiagnosticCollector diagnostics);
}
