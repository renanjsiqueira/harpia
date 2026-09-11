package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.Diagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every example in the catalogue, run through the compiler.
 *
 * <p>Documentation drifts because nothing reads it. Here every entry is a whole specification, and
 * an entry that stops compiling fails this test — so what the catalogue shows is what Harpia does,
 * and someone copying an example gets something that works rather than something that used to.
 *
 * <p>An entry marked with a diagnostic code is the other half of the same promise: the catalogue
 * says what is refused, and the refusal has to actually happen, with that code.
 */
class CatalogTest {

    private static final Path CATALOG = Path.of("docs/catalog.md");

    @TempDir
    Path projectRoot;

    @ParameterizedTest(name = "{0}")
    @MethodSource("entries")
    void everyExampleMeansWhatTheCatalogueSaysItMeans(Entry entry) throws IOException {
        write(entry.spec(), entry.languageVersion());
        List<Diagnostic> diagnostics = new HarpiaCompiler()
                .compile(new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE))
                .diagnostics();

        if (entry.refusedWith().isEmpty()) {
            assertThat(diagnostics)
                    .as("a catalogue example is something to copy, so it has to compile")
                    .isEmpty();
            return;
        }
        assertThat(diagnostics)
                .as("the catalogue says this is refused as %s", entry.refusedWith())
                .extracting(Diagnostic::code)
                .contains(entry.refusedWith());
    }

    @Test
    void theCatalogueIsNotEmptyAndEveryEntryIsNamed() {
        assertThat(entries())
                .hasSizeGreaterThan(10)
                .allSatisfy(entry -> assertThat(entry.title()).isNotBlank());
    }

    @Test
    void everyRefusalInTheCatalogueNamesADeclaredCode() {
        assertThat(entries().filter(entry -> !entry.refusedWith().isEmpty()))
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.refusedWith()).matches("HRP[1-7][0-9]{3}"));
    }

    /**
     * One entry per fenced block whose info string starts with {@code harpia}.
     *
     * <p>The fence is four backticks because the specification inside carries three of its own; the
     * title is the nearest heading above it, so the failure names the entry a reader would look up.
     */
    static Stream<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        String title = "";
        List<String> spec = null;
        String info = "";
        for (String line : read().split("\n", -1)) {
            if (spec == null) {
                if (line.startsWith("#")) {
                    title = line.replaceFirst("^#+\\s*", "").strip();
                } else if (line.startsWith("````harpia")) {
                    info = line.substring("````harpia".length()).strip();
                    spec = new ArrayList<>();
                }
                continue;
            }
            if (line.startsWith("````")) {
                String[] parts = info.isEmpty() ? new String[0] : info.split("\\s+");
                entries.add(new Entry(
                        title,
                        String.join("\n", spec) + "\n",
                        parts.length > 0 && parts[0].startsWith("v")
                                ? Integer.parseInt(parts[0].substring(1))
                                : 1,
                        parts.length > 0 && parts[parts.length - 1].startsWith("HRP")
                                ? parts[parts.length - 1]
                                : ""));
                spec = null;
                info = "";
                continue;
            }
            spec.add(line);
        }
        if (spec != null) {
            throw new AssertionError("unterminated example in " + CATALOG + " under '" + title + "'");
        }
        return entries.stream();
    }

    /** Title, whole specification, the language version it needs, and the code it must produce. */
    record Entry(String title, String spec, int languageVersion, String refusedWith) {
        @Override
        public String toString() {
            return refusedWith.isEmpty() ? title : title + " -> " + refusedWith;
        }
    }

    /**
     * Writes the entry as a project.
     *
     * <p>Most entries are one file. A few need a second entity to point at, and a module declares
     * one, so an HTML comment naming a file splits the block: invisible when the catalogue is read,
     * unambiguous when it is compiled.
     */
    private void write(String spec, int languageVersion) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        String name = "catalog.harpia.md";
        StringBuilder content = new StringBuilder();
        for (String line : spec.split("\n", -1)) {
            if (line.startsWith("<!-- specs/") && line.endsWith(" -->")) {
                if (!content.isEmpty()) {
                    Files.writeString(
                            projectRoot.resolve("specs").resolve(name),
                            content.toString(),
                            StandardCharsets.UTF_8);
                    content.setLength(0);
                }
                name = line.substring("<!-- specs/".length(), line.length() - " -->".length());
                continue;
            }
            content.append(line).append('\n');
        }
        Files.writeString(
                projectRoot.resolve("specs").resolve(name),
                content.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: %d

                project:
                  name: catalog-service
                  group: com.example
                  artifact: catalog-service
                  package: com.example.catalog

                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.3.6"

                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """.formatted(languageVersion), StandardCharsets.UTF_8);
    }

    private static String read() {
        try {
            return Files.readString(CATALOG, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
