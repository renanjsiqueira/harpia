package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Optionality said out loud instead of inferred from a missing modifier.
 *
 * <p>A field without {@code required} is already nullable, but nothing in the generated contract
 * says so: a caller receives a {@code String} and finds out at runtime. {@code Optional<T>} moves
 * that fact into the type the caller is handed, without changing how the value is stored.
 */
class OptionalFieldTest {

    private static final String ENTITY = "src/main/java/com/example/people/domain/Person.java";
    private static final String RESPONSE =
            "src/main/java/com/example/people/dto/PersonResponse.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    @Test
    void anOptionalIsStoredBareAndExposedAsAbsent(@TempDir Path classes) throws IOException {
        project("- nickname: Optional<String>", 1);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Column nickname: Optional<STRING>");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(ENTITY))
                .as("JPA maps by the declared field type and does not understand Optional")
                .contains("private String nickname;")
                .as("what callers are handed says whether the value can be absent")
                .contains("public Optional<String> getNickname() {")
                .contains("return Optional.ofNullable(nickname);")
                .as("the setter takes the value, not the possibility of one")
                .contains("public void setNickname(String nickname) {");
        assertThat(files.get(RESPONSE)).contains("Optional<String> nickname");
        assertThat(files.get(MIGRATION))
                .as("optionality is nullability in the column, exactly as before")
                .contains("nickname varchar(255),");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void optionalAndRequiredContradictEachOther() throws IOException {
        project("- nickname: Optional<String> required", 1);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_OPTIONAL_REQUIRED))
                .isNotEmpty()
                .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot be both optional and mandatory"));
    }

    @Test
    void nestedOptionalityIsRefused() throws IOException {
        project("- nickname: Optional<Optional<String>>", 1);

        assertThat(compile().diagnostics())
                .as("a value that may be absent of being absent says nothing more")
                .isNotEmpty();
    }

    @Test
    void optionalIsNotPartOfV0() throws IOException {
        project("- nickname: Optional<String>", 0);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_UNKNOWN_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("Optional needs harpia.languageVersion 1"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String extraField, int languageVersion) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/person.harpia.md"), """
                # Person

                ## Data

                - id: UUID generated
                - name: String required
                """ + extraField + """

                ## Create Person

                ### Endpoint

                POST /people

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                person = create Person from input
                save person
                return person
                ```

                ### Output

                201 Person
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: VERSION

                project:
                  name: people-service
                  group: com.example
                  artifact: people-service
                  package: com.example.people

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
                """.replace("VERSION", String.valueOf(languageVersion)), StandardCharsets.UTF_8);
    }
}
