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
 * Asking for a slice of a listing instead of all of it.
 *
 * <p>A page is taken from an order, so paging layers on the stable order rather than replacing it.
 * Which page and how many come from the operation's own input, declared like anything else: that is
 * what keeps {@code paged} from inventing fields nobody wrote.
 */
class PaginationTest {

    private static final String REPOSITORY =
            "src/main/java/com/example/ticket/repository/TicketRepository.java";
    private static final String SERVICE =
            "src/main/java/com/example/ticket/service/TicketService.java";
    private static final String REQUEST =
            "src/main/java/com/example/ticket/dto/SearchTicketsRequest.java";

    @TempDir
    Path projectRoot;

    @Test
    void aPagedListingTakesASliceOfTheSameOrder(@TempDir Path classes) throws IOException {
        project("tickets = list Ticket by status sorted by title paged", """
                - status: String required
                - page: Int required
                - size: Int required
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction LIST_BY by status sorted by title asc paged");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(REPOSITORY))
                .contains("List<Ticket> findByStatus(String status, Pageable pageable);");
        assertThat(files.get(SERVICE))
                .as("a page without an order is a page of nothing in particular")
                .contains("PageRequest.of(request.page(), request.size(), "
                        + "Sort.by(Sort.Order.asc(\"title\"), Sort.Order.asc(\"id\")))");
        assertThat(files.get(REQUEST))
                .as("page and size are declared, so the request model shows them")
                .contains("Integer page")
                .contains("Integer size");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void anUnpagedListingStillTakesOnlyAnOrder() throws IOException {
        project("tickets = list Ticket by status", "- status: String required\n");

        assertThat(compile().tree().orElseThrow().files().get(REPOSITORY))
                .contains("List<Ticket> findByStatus(String status, Sort sort);");
    }

    @Test
    void pagingNeedsPageAndSizeInTheInput() throws IOException {
        project("tickets = list Ticket by status paged", "- status: String required\n");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_PAGED_WITHOUT_INPUT))
                .hasSize(2)
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("'- page: Int required'"))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("'- size: Int required'"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String listLine, String input) throws IOException {
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

                """ + input + """

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
