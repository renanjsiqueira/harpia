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
 * The order a listing returns rows in, said by the specification.
 *
 * <p>A sort by a field many rows share leaves ties to the database's mood, and Harpia promises the
 * same bytes for the same input. So a declared order refines the stable one instead of replacing
 * it: the id stays last, as the tiebreaker.
 */
class SortingTest {

    private static final String SERVICE =
            "src/main/java/com/example/ticket/service/TicketService.java";
    private static final String SERVICE_TEST =
            "src/test/java/com/example/ticket/service/TicketServiceTest.java";

    @TempDir
    Path projectRoot;

    @Test
    void aDeclaredOrderRefinesTheStableOneRatherThanReplacingIt(@TempDir Path classes)
            throws IOException {
        project("tickets = list Ticket sorted by priority desc and title");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction LIST_ALL sorted by priority desc and title asc");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .as("ties would otherwise come back in whatever order the database chose")
                .contains("Sort.by(Sort.Order.desc(\"priority\"), Sort.Order.asc(\"title\"), "
                        + "Sort.Order.asc(\"id\"))");
        assertThat(files.get(SERVICE_TEST))
                .as("the generated test has to assert the same order the service asks for")
                .contains("Sort.by(Sort.Order.desc(\"priority\"), Sort.Order.asc(\"title\"), "
                        + "Sort.Order.asc(\"id\"))");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void anUndeclaredOrderStaysTheStableOne() throws IOException {
        project("tickets = list Ticket");

        SortedMap<String, String> files = compile().tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .as("nothing was declared, so nothing changes")
                .contains("repository.findAll(Sort.by(\"id\"))");
    }

    @Test
    void ascendingIsWhatAnUnsaidDirectionMeans() throws IOException {
        project("tickets = list Ticket sorted by title");

        assertThat(compile().tree().orElseThrow().files().get(SERVICE))
                .contains("Sort.by(Sort.Order.asc(\"title\"), Sort.Order.asc(\"id\"))");
    }

    @Test
    void sortingNeedsAFieldTheEntityStores() throws IOException {
        project("tickets = list Ticket sorted by nickname");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot sort 'Ticket' by 'nickname'"));
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
                - title: String required
                - priority: Int required

                ## Query List Tickets

                ### Endpoint

                GET /tickets

                ### Access

                public

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
