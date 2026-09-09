package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Target-independent golden fixtures, taken from {@code harpia inspect}.
 *
 * <p>They are the contract that survives adding a target: the same specification must produce the
 * same syntax tree, the same symbols, the same Business IR and the same Application IR whichever
 * target is configured. When a second target arrives it gets its own {@code fixtures/targets/<id>}
 * directory for generated code, and these files stay untouched.
 *
 * <p>They are also readable proof of the boundary: no word in them names a language or a framework.
 */
class SemanticFixtureTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/semantic/customer");

    @ParameterizedTest
    @EnumSource(Stage.class)
    void everyStageMatchesItsGolden(Stage stage) {
        assertThat(render(stage)).isEqualTo(golden(stage.id() + ".txt"));
    }

    @Test
    void noTargetIndependentStageNamesALanguageOrAFramework() {
        for (Stage stage : Stage.values()) {
            assertThat(render(stage).toLowerCase(Locale.ROOT))
                    .as("%s must describe intent, not an implementation", stage.id())
                    .doesNotContain("spring", "jpa", "bigdecimal", "repository", "controller");
        }
    }

    @Test
    void inspectionShowsWhatWasCompiledRatherThanARecomputation() {
        CompileResult result = compile();

        assertThat(result.stages().syntax().modules()).isNotEmpty();
        assertThat(result.stages().business()).isPresent();
        assertThat(result.stages().application()).isPresent();
    }

    static String render(Stage stage) {
        return Inspector.render(compile(), stage).orElseThrow();
    }

    private static CompileResult compile() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer"), CompileRequest.Mode.VALIDATE));
        assertThat(result.diagnostics()).isEmpty();
        return result;
    }

    private static String golden(String name) {
        try {
            return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
