package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compiles every generated Java source against the real APIs it targets.
 *
 * <p>{@code jakarta.persistence-api} and {@code spring-data-jpa} are test-scoped dependencies of
 * the compiler for exactly this reason: asserting on strings proves the generator produced text,
 * while {@code javac} proves it produced Java.
 */
class GeneratedSourcesCompileTest {

    @ParameterizedTest
    @ValueSource(strings = {"examples/customer", "examples/business-logic/pricing"})
    void everyGeneratedSourceCompiles(String project, @TempDir Path classes) throws IOException {
        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(Path.of(project)));
        assertThat(result.diagnostics()).isEmpty();

        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }
}
