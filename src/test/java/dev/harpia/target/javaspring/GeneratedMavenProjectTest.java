package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves that the generated tree is a real offline Maven project, not only plausible Java text. */
class GeneratedMavenProjectTest {

    @Test
    void generatedCustomerProjectPassesMavenTest(@TempDir Path generated) throws Exception {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer")));
        assertThat(result.diagnostics()).isEmpty();
        write(generated, result.tree().orElseThrow().files());

        Path log = generated.resolve("maven-test.log");
        Process process = new ProcessBuilder("mvn", "-q", "-o", "test")
                .directory(generated.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = Files.exists(log)
                ? Files.readString(log, StandardCharsets.UTF_8)
                : "Maven produced no log";
        assertThat(finished).as("generated Maven build timed out: %s", output).isTrue();
        assertThat(process.exitValue()).as("generated Maven build failed: %s", output).isZero();
    }

    private static void write(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
    }
}
