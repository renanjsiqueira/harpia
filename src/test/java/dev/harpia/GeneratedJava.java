package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Runs {@code javac} over a generated tree.
 *
 * <p>Asserting on strings proves the generator produced text; only a compiler proves it produced
 * Java. An unresolved import is invisible to a text assertion and fatal to the person who runs the
 * generated project, so every test that cares about imports should end here.
 */
public final class GeneratedJava {

    private GeneratedJava() {
    }

    /** Fails unless every {@code .java} file in the tree compiles against the real APIs. */
    public static void compiles(Map<String, String> files, Path classes) throws IOException {
        List<JavaFileObject> sources = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            if (file.getKey().endsWith(".java")) {
                sources.add(new InMemorySource(file.getKey(), file.getValue()));
            }
        }
        assertThat(sources).as("the project must generate Java at all").isNotEmpty();

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac).as("a JDK is required").isNotNull();
        StringBuilder errors = new StringBuilder();
        try (StandardJavaFileManager manager = javac.getStandardFileManager(null, null, null)) {
            boolean compiled = javac.getTask(
                            null,
                            manager,
                            diagnostic -> errors.append(diagnostic).append('\n'),
                            List.of(
                                    "-d", classes.toString(),
                                    "-classpath", System.getProperty("java.class.path"),
                                    "-proc:none"),
                            null,
                            sources)
                    .call();
            assertThat(compiled).as("generated Java must compile: %s", errors).isTrue();
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
