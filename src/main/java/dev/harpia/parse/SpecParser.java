package dev.harpia.parse;

import dev.harpia.LanguageVersion;
import dev.harpia.ast.BlockNode;
import dev.harpia.ast.MarkdownStructure;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.Sections.Section;
import dev.harpia.source.SourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Builds one {@link ModuleAst} from the structural CommonMark view. */
public final class SpecParser {

    private static final Pattern MODULE_NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

    private SpecParser() {
    }

    public static Optional<ModuleAst> parse(
            SourceFile source,
            LanguageVersion languageVersion,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(source, "source");
        return parse(MarkdownStructure.parse(source), languageVersion, diagnostics);
    }

    public static Optional<ModuleAst> parse(
            MarkdownStructure markdown,
            LanguageVersion languageVersion,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(languageVersion, "languageVersion");
        Objects.requireNonNull(diagnostics, "diagnostics");
        DeclarationParserRegistry registry =
                DeclarationParserRegistry.forLanguageVersion(languageVersion);

        List<BlockNode> blocks = markdown.blocks();
        List<BlockNode> moduleHeadings = blocks.stream()
                .filter(block -> block.kind() == BlockNode.Kind.HEADING)
                .filter(block -> block.headingLevel() == 1)
                .toList();

        boolean valid = true;
        if (moduleHeadings.size() != 1) {
            SourceRef where = moduleHeadings.size() > 1
                    ? moduleHeadings.get(1).raw().where()
                    : SourceRef.file(markdown.source().relativePath());
            diagnostics.error(
                    ErrorCodes.SYNTAX_H1,
                    "expected exactly one module H1 but found " + moduleHeadings.size(),
                    where);
            valid = false;
        }

        BlockNode moduleHeading = moduleHeadings.isEmpty() ? null : moduleHeadings.getFirst();
        String moduleName = moduleHeading == null ? "Invalid" : moduleHeading.headingText();
        SourceRef moduleWhere = moduleHeading == null
                ? SourceRef.file(markdown.source().relativePath())
                : moduleHeading.raw().where();
        if (moduleHeading != null && !MODULE_NAME.matcher(moduleName).matches()) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_ENTITY_NAME,
                    "invalid module name '" + moduleName + "'; expected PascalCase",
                    moduleWhere);
            valid = false;
        }

        List<Section> sections = Sections.at(blocks, 2);
        List<Section> dataSections = sections.stream()
                .filter(section -> section.name().equals("Data"))
                .toList();
        boolean hasDataSection = !dataSections.isEmpty();
        boolean hasMemberSection = sections.stream()
                .anyMatch(section -> !section.name().equals("Data"));

        List<DeclarationAst> declarations = new ArrayList<>();
        for (Section section : sections) {
            Optional<DeclarationAst> declaration =
                    registry.parse(moduleName, section, diagnostics);
            if (declaration.isPresent()) {
                declarations.add(declaration.orElseThrow());
            } else {
                valid = false;
            }
        }

        if (dataSections.size() > 1) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DATA_SECTION,
                    "expected at most one '## Data' section but found " + dataSections.size(),
                    dataSections.get(1).heading().raw().where());
            valid = false;
        }

        if (!hasDataSection && !hasMemberSection) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DATA_SECTION,
                    "module '" + moduleName
                            + "' declares nothing; add '## Data' or at least one declaration",
                    moduleWhere);
            valid = false;
        }
        boolean declaresUseCase = declarations.stream()
                .anyMatch(SpecAst.UseCaseDeclaration.class::isInstance);
        // V0 keeps an operation in the module that declares the entity it works on. From V1 an
        // operation names its entity, so it may live in a module of its own.
        if (languageVersion == LanguageVersion.V0 && !hasDataSection && declaresUseCase) {
            diagnostics.error(
                    ErrorCodes.SYNTAX_DATA_SECTION,
                    "module '" + moduleName
                            + "' declares use cases and therefore requires a '## Data' section",
                    moduleWhere);
            valid = false;
        }

        if (!valid) {
            return Optional.empty();
        }
        return Optional.of(new ModuleAst(
                markdown.source().relativePath(), moduleName, declarations, moduleWhere));
    }
}
