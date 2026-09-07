package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compiles every generated Java source against the real APIs it targets.
 *
 * <p>{@code jakarta.persistence-api} and {@code spring-data-jpa} are test-scoped dependencies of
 * the compiler for exactly this reason: asserting on strings proves the generator produced text,
 * while {@code javac} proves it produced Java. Running Maven on the generated project is the next
 * gate (E0.9) and needs the service layer to exist first.
 */
class GeneratedSourcesCompileTest {

    @ParameterizedTest
    @ValueSource(strings = {"examples/customer", "examples/business-logic/pricing"})
    void everyGeneratedSourceCompiles(String project, @TempDir Path classes) throws IOException {
        CompileResult result = new HarpiaCompiler().compile(new CompileRequest(Path.of(project)));
        assertThat(result.diagnostics()).isEmpty();

        List<JavaFileObject> sources = new ArrayList<>();
        for (Map.Entry<String, String> file : result.tree().orElseThrow().files().entrySet()) {
            if (file.getKey().endsWith(".java")) {
                sources.add(new InMemorySource(file.getKey(), file.getValue()));
            }
        }
        assertThat(sources).as("the project must generate Java at all").isNotEmpty();

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac).as("a JDK is required").isNotNull();
        StringBuilder errors = new StringBuilder();
        try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, null)) {
            boolean compiled = javac.getTask(
                            null,
                            files,
                            diagnostic -> errors.append(diagnostic).append('\n'),
                            List.of(
                                    "-d", classes.toString(),
                                    "-classpath", System.getProperty("java.class.path"),
                                    "-proc:none"),
                            null,
                            sources)
                    .call();
            assertThat(compiled)
                    .as("generated Java must compile: %s", errors)
                    .isTrue();
        }
    }

    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String code;

        private InMemorySource(String path, String code) {
            super(URI.create("string:///" + path), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
