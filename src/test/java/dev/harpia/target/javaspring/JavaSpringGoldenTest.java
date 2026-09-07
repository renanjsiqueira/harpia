package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Reviews the complete first Java/Spring vertical as a byte-for-byte golden tree. */
class JavaSpringGoldenTest {

    private static final Path EXPECTED =
            Path.of("src/test/resources/fixtures/targets/java-spring/customer");

    @Test
    void customerProjectMatchesTheVersionedTargetGolden() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer")));

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files()).isEqualTo(expected());
    }

    private static Map<String, String> expected() {
        try (Stream<Path> files = Files.walk(EXPECTED)) {
            TreeMap<String, String> result = new TreeMap<>();
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    result.put(
                            EXPECTED.relativize(path).toString().replace('\\', '/'),
                            Files.readString(path, StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
