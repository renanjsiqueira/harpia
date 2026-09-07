package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * For a fixed target and compiler version, compiling the same specification twice must produce the
 * same bytes. No clock, no random, no unordered iteration, no model.
 */
class DeterminismTest {

    @ParameterizedTest
    @ValueSource(strings = {"examples/customer", "examples/business-logic/pricing"})
    void repeatedCompilationHashesToTheSameTree(String project) {
        String first = hash(project);
        String second = hash(project);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void everyGeneratedFileIsFreeOfTimestampsAndHostSpecificValues() {
        SortedMap<String, String> files = compile("examples/customer");

        assertThat(files.values()).allSatisfy(content -> assertThat(content)
                .doesNotContain(String.valueOf(java.time.Year.now().getValue()) + "-")
                .doesNotContain(System.getProperty("user.name"))
                .doesNotContain(System.getProperty("user.dir")));
    }

    @Test
    void validateAndBuildCompileTheSameTree() {
        CompileResult validated = new HarpiaCompiler().compile(
                new CompileRequest(java.nio.file.Path.of("examples/customer"),
                        CompileRequest.Mode.VALIDATE));
        CompileResult built = new HarpiaCompiler().compile(
                new CompileRequest(java.nio.file.Path.of("examples/customer"),
                        CompileRequest.Mode.BUILD));

        assertThat(validated.tree().orElseThrow().files())
                .isEqualTo(built.tree().orElseThrow().files());
    }

    private static String hash(String project) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> file : compile(project).entrySet()) {
                digest.update(file.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(file.getValue().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static SortedMap<String, String> compile(String project) {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(java.nio.file.Path.of(project)));
        assertThat(result.hasErrors()).isFalse();
        return result.tree().orElseThrow().files();
    }
}
