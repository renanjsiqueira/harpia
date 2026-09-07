package dev.harpia.source;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFileTest {

    @TempDir
    Path projectRoot;

    @Test
    void stripsUtf8BomAndNormalizesCrLfAndLoneCr() throws IOException {
        Path source = projectRoot.resolve("specs/customer.harpia.md");
        Files.createDirectories(source.getParent());
        byte[] text = "\uFEFF# Customer\r\n\rdescription\n".getBytes(StandardCharsets.UTF_8);
        Files.write(source, text);
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        SourceFile result = SourceFile.read(projectRoot, source, diagnostics).orElseThrow();

        assertThat(result.relativePath()).isEqualTo("specs/customer.harpia.md");
        assertThat(result.content()).isEqualTo("# Customer\n\ndescription\n");
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void invalidUtf8ProducesACompilerDiagnostic() throws IOException {
        Path source = projectRoot.resolve("specs/broken.harpia.md");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[] {(byte) 0xc3, (byte) 0x28});
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(SourceFile.read(projectRoot, source, diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(ErrorCodes.IO_NOT_UTF8);
                    assertThat(diagnostic.where()).hasValueSatisfying(where ->
                            assertThat(where.file()).isEqualTo("specs/broken.harpia.md"));
                });
    }

    @Test
    void refusesToReadOutsideTheProjectRoot(@TempDir Path tempDirectory) throws IOException {
        Path nestedProjectRoot = Files.createDirectory(tempDirectory.resolve("project"));
        Path outside = Files.createFile(tempDirectory.resolve("outside.harpia.md"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(SourceFile.read(nestedProjectRoot, outside, diagnostics)).isEmpty();
        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly(ErrorCodes.IO_PATH_ESCAPE);
    }
}
