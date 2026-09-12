package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An index the specification asks for.
 *
 * <p>A unique column already has one behind its constraint. {@code indexed} is for the other case:
 * a column that is read often and constrains nothing, which no rule the compiler already knows
 * would have produced.
 */
class IndexedFieldTest {

    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    @Test
    void anIndexedFieldGetsAnIndexAndNoConstraint() throws IOException {
        project("- status: String required indexed");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Column status: STRING required indexed");

        String migration = result.tree().orElseThrow().files().get(MIGRATION);
        assertThat(migration)
                .as("an index is not a constraint, and its name says so")
                .contains("CREATE INDEX ix_ticket_status ON ticket (status);")
                .doesNotContain("CONSTRAINT ix_ticket_status");
    }

    @Test
    void aUniqueFieldAlreadyHasItsIndex() throws IOException {
        project("- status: String required");

        String migration = compile().tree().orElseThrow().files().get(MIGRATION);
        assertThat(migration)
                .as("the unique constraint is what indexes a unique column")
                .contains("CONSTRAINT uq_ticket_code UNIQUE (code)")
                .doesNotContain("CREATE INDEX ix_ticket_code");
    }

    @Test
    void askingToIndexAUniqueColumnIsRefused() throws IOException {
        project("- status: String required unique indexed");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_FIELD_LINE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("'unique' already indexes the column; 'indexed' adds nothing"));
    }

    @Test
    void anIndexOnNothingIsNotGenerated() throws IOException {
        project("- status: String required");

        assertThat(compile().tree().orElseThrow().files().get(MIGRATION))
                .as("nothing asked for an index, so none is created")
                .doesNotContain("CREATE INDEX");
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String statusField) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/ticket.harpia.md"), """
                # Ticket

                ## Data

                - id: UUID generated
                - code: String required unique
                """ + statusField + """

                ## Query List Tickets

                ### Endpoint

                GET /tickets

                ### Access

                public

                ### Flow

                ```flow
                tickets = list Ticket
                return tickets
                ```

                ### Output

                200 List<Ticket>
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: idx-service
                  group: com.example
                  artifact: idx-service
                  package: com.example.idx

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
                """, StandardCharsets.UTF_8);
    }
}
