package dev.harpia.source;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecDiscoveryTest {

    @TempDir
    Path projectRoot;

    @Test
    void returnsOnlySpecsSortedByProjectRelativePath() throws IOException {
        write("specs/z-last.harpia.md");
        write("specs/nested/b.harpia.md");
        write("specs/a-first.harpia.md");
        write("specs/nested/ignored.md");
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        List<String> first = relative(SpecDiscovery.discover(projectRoot, Path.of("specs"), diagnostics));
        List<String> second = relative(SpecDiscovery.discover(projectRoot, Path.of("specs"), diagnostics));

        assertThat(first).containsExactly(
                "specs/a-first.harpia.md",
                "specs/nested/b.harpia.md",
                "specs/z-last.harpia.md");
        assertThat(second).containsExactlyElementsOf(first);
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void doesNotFollowFileOrDirectorySymlinks() throws IOException {
        Path realFile = write("outside/secret.harpia.md");
        Path realDirectory = projectRoot.resolve("outside/tree");
        Files.createDirectories(realDirectory);
        Files.writeString(realDirectory.resolve("nested.harpia.md"), "# Nested\n", StandardCharsets.UTF_8);
        Path specs = projectRoot.resolve("specs");
        Files.createDirectories(specs);
        Files.createSymbolicLink(specs.resolve("file.harpia.md"), realFile);
        Files.createSymbolicLink(specs.resolve("directory"), realDirectory);
        write("specs/real.harpia.md");
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        List<Path> result = SpecDiscovery.discover(projectRoot, Path.of("specs"), diagnostics);

        assertThat(relative(result)).containsExactly("specs/real.harpia.md");
        assertThat(diagnostics.diagnostics())
                .hasSize(2)
                .allSatisfy(diagnostic ->
                        assertThat(diagnostic.code()).isEqualTo(ErrorCodes.IO_SYMLINK_IGNORED));
        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.where().orElseThrow().file())
                .containsExactly("specs/directory", "specs/file.harpia.md");
    }

    @Test
    void rejectsSpecsDirectoryOutsideProject() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(SpecDiscovery.discover(projectRoot, Path.of("../elsewhere"), diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly(ErrorCodes.IO_PATH_ESCAPE);
    }

    @Test
    void reportsWhenThereAreNoSpecsToCompile() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(SpecDiscovery.discover(projectRoot, Path.of("specs"), diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(ErrorCodes.CONFIG_NO_SPECS);
                    assertThat(diagnostic.where()).hasValueSatisfying(where ->
                            assertThat(where.file()).isEqualTo("specs"));
                });
    }

    private Path write(String relativePath) throws IOException {
        Path path = projectRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "# Spec\n", StandardCharsets.UTF_8);
        return path;
    }

    private List<String> relative(List<Path> paths) {
        return paths.stream()
                .map(projectRoot::relativize)
                .map(Path::toString)
                .map(path -> path.replace('\\', '/'))
                .toList();
    }
}
