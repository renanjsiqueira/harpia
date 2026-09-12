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
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves that the generated tree is a real offline Maven project, not only plausible Java text. */
class GeneratedMavenProjectTest {

    /**
     * The last gate a generated project has to pass.
     *
     * <p>{@code package} runs the tests and then builds the artifact, so one invocation answers
     * both questions a person asks of a generated project: does it pass, and can I run it. An
     * archive that compiles but has no launcher is not something anyone can start.
     */
    @Test
    void generatedCustomerProjectPassesMavenPackageAndYieldsAnExecutableJar(@TempDir Path generated)
            throws Exception {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer")));
        assertThat(result.diagnostics()).isEmpty();
        write(generated, result.tree().orElseThrow().files());

        Path log = generated.resolve("maven-package.log");
        Process process = new ProcessBuilder("mvn", "-q", "-o", "package")
                .directory(generated.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = Files.exists(log)
                ? Files.readString(log, StandardCharsets.UTF_8)
                : "Maven produced no log";
        assertThat(finished).as("generated Maven build timed out: %s", output).isTrue();
        assertThat(process.exitValue()).as("generated Maven build failed: %s", output).isZero();

        Path jar = jar(generated.resolve("target"));
        try (JarFile archive = new JarFile(jar.toFile())) {
            Manifest manifest = archive.getManifest();
            assertThat(manifest.getMainAttributes().getValue("Main-Class"))
                    .as("a jar without a launcher cannot be started with java -jar")
                    .isEqualTo("org.springframework.boot.loader.launch.JarLauncher");
            assertThat(manifest.getMainAttributes().getValue("Start-Class"))
                    .as("the launcher has to start the application Harpia generated")
                    .isEqualTo("com.example.customer.CustomerServiceApplication");
            assertThat(archive.stream().map(java.util.jar.JarEntry::getName))
                    .as("an executable jar carries its dependencies")
                    .anyMatch(name -> name.startsWith("BOOT-INF/lib/"));
        }
    }

    private static Path jar(Path target) throws IOException {
        try (Stream<Path> entries = Files.list(target)) {
            return entries
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no jar in " + target));
        }
    }

    private static void write(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
    }
}
