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
 * Reporting which page came back, not just its rows.
 *
 * <p>{@code List<Entity>} over a paged query hands the caller records with no way to ask for the
 * next ones. {@code Page<Entity>} carries what the caller needs to continue, and the count comes
 * from the same query that took the slice.
 */
class PageOutputTest {

    private static final String ENVELOPE =
            "src/main/java/com/example/ticket/dto/PageResponse.java";
    private static final String REPOSITORY =
            "src/main/java/com/example/ticket/repository/TicketRepository.java";
    private static final String SERVICE =
            "src/main/java/com/example/ticket/service/TicketService.java";

    @TempDir
    Path projectRoot;

    @Test
    void aPageOutputCarriesWhatTheCallerNeedsToContinue(@TempDir Path classes) throws IOException {
        project("tickets = list Ticket by status paged", "200 Page<Ticket>");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Result 200 PAGE TicketResponse");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(ENVELOPE))
                .as("what a page reports about itself does not depend on what it holds")
                .contains("public record PageResponse<T>(")
                .contains("List<T> content")
                .contains("long totalElements")
                .contains("int totalPages");
        assertThat(files.get(REPOSITORY))
                .as("the count comes from the query that took the slice")
                .contains("Page<Ticket> findByStatus(String status, Pageable pageable);");
        assertThat(files.get(SERVICE))
                .contains("public PageResponse<TicketResponse> searchTickets(")
                .contains("tickets.getTotalElements(),")
                .contains("tickets.getTotalPages());");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aPageOutputNeedsAPagedListingToReport() throws IOException {
        project("tickets = list Ticket by status", "200 Page<Ticket>");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_PAGED_WITHOUT_INPUT))
                .isNotEmpty()
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("needs a paged listing in the flow"));
    }

    @Test
    void aPagedListingMayStillAnswerWithJustTheRows() throws IOException {
        project("tickets = list Ticket by status paged", "200 List<Ticket>");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files())
                .as("the envelope is emitted only where something reports a page")
                .doesNotContainKey(ENVELOPE);
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String listLine, String output) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/ticket.harpia.md"), """
                # Ticket

                ## Data

                - id: UUID generated
                - title: String required
                - status: String required

                ## Query Search Tickets

                ### Endpoint

                GET /tickets

                ### Access

                public

                ### Input

                - status: String required
                - page: Int required
                - size: Int required

                ### Flow

                ```flow
                """ + listLine + """

                return tickets
                ```

                ### Output

                """ + output, StandardCharsets.UTF_8);
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
