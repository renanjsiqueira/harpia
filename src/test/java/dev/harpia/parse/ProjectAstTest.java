package dev.harpia.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harpia.LanguageVersion;
import dev.harpia.diag.SourceRef;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAstTest {

    @Test
    void modulesAreStoredInDeterministicSourcePathOrder() {
        ModuleAst later = module("specs/zeta.harpia.md", "Zeta");
        ModuleAst earlier = module("specs/alpha.harpia.md", "Alpha");

        ProjectAst project = new ProjectAst(LanguageVersion.V0, List.of(later, earlier));

        assertThat(project.languageVersion()).isEqualTo(LanguageVersion.V0);
        assertThat(project.modules()).extracting(ModuleAst::file)
                .containsExactly("specs/alpha.harpia.md", "specs/zeta.harpia.md");
    }

    @Test
    void projectAndModuleOwnImmutableSnapshots() {
        List<DeclarationAst> declarations = new ArrayList<>();
        ModuleAst module = new ModuleAst(
                "specs/sample.harpia.md",
                "Sample",
                declarations,
                SourceRef.file("specs/sample.harpia.md"));
        List<ModuleAst> modules = new ArrayList<>(List.of(module));

        ProjectAst project = new ProjectAst(LanguageVersion.V0, modules);
        declarations.add(new SpecAst.EntityDeclaration(
                "Sample", List.of(), SourceRef.file("specs/sample.harpia.md")));
        modules.clear();

        assertThat(project.modules()).containsExactly(module);
        assertThat(module.declarations()).isEmpty();
        assertThatThrownBy(() -> project.modules().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aSourceFileCanOnlyOccurOnceInTheProjectTree() {
        ModuleAst first = module("specs/sample.harpia.md", "First");
        ModuleAst second = module("specs/sample.harpia.md", "Second");

        assertThatThrownBy(() -> new ProjectAst(LanguageVersion.V0, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repeats source file specs/sample.harpia.md");
    }

    private static ModuleAst module(String file, String name) {
        return new ModuleAst(file, name, List.of(), SourceRef.file(file));
    }
}
