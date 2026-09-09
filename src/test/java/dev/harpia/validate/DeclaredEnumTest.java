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
 * A type the project names, rather than one the compiler ships.
 *
 * <p>An enum is the first nominal type in Harpia, so it is also the first field type that is not a
 * scalar. What that costs is deliberate: every mapping that turns a field into Java, SQL or a
 * sample value now has to answer for it, instead of silently treating it as text.
 */
class DeclaredEnumTest {

    private static final String ENUM =
            "src/main/java/com/example/ticket/domain/TicketStatus.java";
    private static final String ENTITY = "src/main/java/com/example/ticket/domain/Ticket.java";
    private static final String REQUEST =
            "src/main/java/com/example/ticket/dto/CreateTicketRequest.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    @Test
    void aDeclaredEnumBecomesATypeAColumnAndAConstraint(@TempDir Path classes) throws IOException {
        project("""
                ## Enum TicketStatus

                - open
                - in_progress
                - closed
                """, "TicketStatus");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.SYMBOLS).orElseThrow())
                .contains("TicketStatus enum(open, in_progress, closed)");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Enum TicketStatus(open, in_progress, closed)")
                .contains("Column status: TicketStatus required");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(ENUM))
                .contains("public enum TicketStatus {")
                .contains("    OPEN,\n    IN_PROGRESS,\n    CLOSED;");
        assertThat(files.get(ENTITY))
                .as("the name is stored, not the ordinal, so reordering the values is safe")
                .contains("@Enumerated(EnumType.STRING)")
                .contains("private TicketStatus status;");
        assertThat(files.get(REQUEST)).contains("@NotNull TicketStatus status");
        assertThat(files.get(MIGRATION)).contains("status varchar(64) NOT NULL");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aFieldCannotNameATypeNothingDeclares() throws IOException {
        project("", "Money");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_UNKNOWN_TYPE))
                .isNotEmpty()
                .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("unknown type 'Money'")
                        .contains("no declaration in this project provides it"));
    }

    @Test
    void anEntityFieldAloneIsEnoughToDemandTheType() throws IOException {
        project("""
                ## Enum TicketStatus

                - open
                """, "TicketStatus");
        Files.writeString(
                projectRoot.resolve("specs/ticket.harpia.md"),
                Files.readString(projectRoot.resolve("specs/ticket.harpia.md"),
                                StandardCharsets.UTF_8)
                        .replace("- title: String required\n- status:",
                                "- price: Money\n- title: String required\n- status:"),
                StandardCharsets.UTF_8);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_UNKNOWN_TYPE))
                .isNotEmpty()
                .allSatisfy(diagnostic ->
                        assertThat(diagnostic.message()).contains("unknown type 'Money'"));
    }

    @Test
    void anEnumWithoutValuesIsRefused() throws IOException {
        project("""
                ## Enum TicketStatus
                """, "TicketStatus");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_ENUM_VALUE))
                .singleElement()
                .satisfies(diagnostic ->
                        assertThat(diagnostic.message()).contains("declares no values"));
    }

    @Test
    void aValueThatIsNotHarpiaVocabularyIsRefused() throws IOException {
        project("""
                ## Enum TicketStatus

                - Open
                """, "TicketStatus");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_ENUM_VALUE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("invalid enum value 'Open'")
                        .contains("lower_snake_case"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String declaration, String statusType) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/ticket.harpia.md"), """
                # Ticket

                DECLARATION

                ## Data

                - id: UUID generated
                - title: String required
                - status: STATUS required

                ## Create Ticket

                ### Endpoint

                POST /tickets

                ### Access

                public

                ### Input

                - title: String required
                - status: STATUS required

                ### Flow

                ```flow
                validate input
                ticket = create Ticket from input
                save ticket
                return ticket
                ```

                ### Output

                201 Ticket
                """
                .replace("DECLARATION", declaration)
                .replace("STATUS", statusType), StandardCharsets.UTF_8);
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
