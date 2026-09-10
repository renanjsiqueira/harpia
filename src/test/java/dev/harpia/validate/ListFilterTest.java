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
 * Listing the records that match a value, rather than all of them.
 *
 * <p>It is the counterpart of {@code find}: a find assigns to one variable, so its field must be
 * unique; a filtered list holds many, so a shared value is exactly what it is asking for.
 */
class ListFilterTest {

    private static final String REPOSITORY =
            "src/main/java/com/example/ticket/repository/TicketRepository.java";
    private static final String SERVICE =
            "src/main/java/com/example/ticket/service/TicketService.java";

    @TempDir
    Path projectRoot;

    @Test
    void afilteredListDerivesItsQueryAndKeepsAStableOrder(@TempDir Path classes)
            throws IOException {
        project("tickets = list Ticket by status and owner");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction LIST_BY by status and owner");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(REPOSITORY))
                .as("Spring Data reads the method name, so the filter is the name")
                .contains("List<Ticket> findByStatusAndOwner(String status, String owner, "
                        + "Sort sort);");
        assertThat(files.get(SERVICE))
                .as("a filtered list keeps the stable order an unfiltered one already has")
                .contains("repository.findByStatusAndOwner(request.status(), request.owner(), "
                        + "Sort.by(\"id\"))");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aFilterDoesNotNeedTheFieldToBeUnique() throws IOException {
        project("tickets = list Ticket by status");

        assertThat(compile().diagnostics())
                .as("many records sharing a value is what a filtered list is for")
                .isEmpty();
    }

    @Test
    void aFilterNeedsAnInputToSearchWith() throws IOException {
        project("tickets = list Ticket by priority");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .isNotEmpty()
                .anySatisfy(diagnostic ->
                        assertThat(diagnostic.message()).contains("has no field 'priority'"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String listLine) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/ticket.harpia.md"), """
                # Ticket

                ## Data

                - id: UUID generated
                - status: String required
                - owner: String required

                ## Query Search Tickets

                ### Endpoint

                GET /tickets

                ### Access

                public

                ### Input

                - status: String required
                - owner: String required

                ### Flow

                ```flow
                """ + listLine + """

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
                  name: ticket-service
                  group: com.example
                  artifact: ticket-service
                  package: com.example.ticket

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
