package dev.harpia.source;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.DiagnosticCollector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BindingDiscoveryTest {

    @TempDir
    Path projectRoot;

    @Test
    void aMissingBindingsDirectoryMeansThereAreNoExternalBindings() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(BindingDiscovery.discover(
                projectRoot, Path.of("bindings"), diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void bindingSourcesAreDiscoveredInStableProjectRelativeOrder() throws IOException {
        write("bindings/z.harpia.md");
        write("bindings/nested/a.harpia.md");
        write("bindings/ignored.md");
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        List<String> files = BindingDiscovery.discover(
                        projectRoot, Path.of("bindings"), diagnostics).stream()
                .map(projectRoot::relativize)
                .map(path -> path.toString().replace('\\', '/'))
                .toList();

        assertThat(files).containsExactly(
                "bindings/nested/a.harpia.md", "bindings/z.harpia.md");
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    private void write(String relative) throws IOException {
        Path path = projectRoot.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "# HTTP Bindings\n", StandardCharsets.UTF_8);
    }
}
