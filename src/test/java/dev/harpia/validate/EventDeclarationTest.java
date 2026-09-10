package dev.harpia.validate;

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
 * An event is something that happened, announced to whoever cares.
 *
 * <p>It has no caller and no result — nothing waits for it — so the whole declaration is what it
 * carries. These tests are about that payload crossing the compiler without any transport being
 * chosen for it.
 */
class EventDeclarationTest {

    @TempDir
    Path projectRoot;

    @Test
    void anEventCrossesEveryStageWithoutATransport() throws IOException {
        project(1, """
                ## Event PurchasePlaced

                ### Payload

                - purchase: Reference<Purchase> required
                - status: Status required
                - total: Decimal required
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("Event PurchasePlaced")
                .contains("Payload purchase: Reference<Purchase> required");
        assertThat(Inspector.render(result, Stage.SYMBOLS).orElseThrow())
                .contains("Namespace events")
                .contains("PurchasePlaced payload(purchase: Reference<Purchase> required, "
                        + "status: Status required, total: Decimal required)");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Event PurchasePlaced")
                .contains("Payload status: Status required");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .as("no stage names a broker, a topic or a listener")
                .contains("Event PurchasePlaced")
                .contains("Payload total: DECIMAL required")
                .doesNotContain("topic", "broker", "listener");
    }

    @Test
    void anEventAnnouncesAnIdentityAndNotTheRowBehindIt() throws IOException {
        project(1, """
                ## Event PurchasePlaced

                ### Payload

                - purchase: Purchase required
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_EVENT_PAYLOAD_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("is an entity, whose lifetime the reader does not share")
                        .contains("announce 'Reference<Purchase>' instead"));
    }

    @Test
    void anEventWithNoPayloadSaysNothingHappenedToAnything() throws IOException {
        project(1, """
                ## Event PurchasePlaced
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_EVENT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .isEqualTo("event 'PurchasePlaced' is missing '### Payload'"));
    }

    @Test
    void anEventDeclaresOnlyItsPayload() throws IOException {
        project(1, """
                ## Event PurchasePlaced

                ### Payload

                - status: Status required

                ### Output

                200 Purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_EVENT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("unknown event subsection '### Output'")
                        .contains("an event declares only '### Payload'"));
    }

    @Test
    void thePayloadCannotRepeatAField() throws IOException {
        project(1, """
                ## Event PurchasePlaced

                ### Payload

                - status: Status required
                - status: Status required
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_DUPLICATE_EVENT_FIELD))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("repeats payload field 'status'"));
    }

    @Test
    void twoEventsCannotShareAName() throws IOException {
        project(1, """
                ## Event PurchasePlaced

                ### Payload

                - status: Status required

                ## Event PurchasePlaced

                ### Payload

                - total: Decimal required
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_DUPLICATE_EVENT))
                .as("both declarations are reported, so neither site looks innocent")
                .hasSize(2)
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly(
                        "event 'PurchasePlaced' is declared more than once",
                        "duplicate event 'PurchasePlaced'");
    }

    @Test
    void v0HasNoEvents() throws IOException {
        project(0, """
                ## Event PurchasePlaced

                ### Payload

                - status: Status required
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SYNTAX_DECLARATION_TOO_NEW))
                .extracting(diagnostic -> diagnostic.message())
                .contains("'## Event PurchasePlaced' declares an Event, which needs "
                        + "harpia.languageVersion 1");
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE));
    }

    private void project(int languageVersion, String event) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/purchase.harpia.md"), """
                # Purchase

                ## Enum Status

                - placed
                - cancelled

                ## Data

                - id: UUID generated
                - status: Status required
                - total: Decimal required

                %s
                ## Get Purchase

                ### Endpoint

                GET /purchases/{id}

                ### Access

                public

                ### Flow

                ```flow
                purchase = load Purchase by id
                return purchase
                ```

                ### Output

                200 Purchase

                ### Errors

                - not found -> 404
                """.formatted(event), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: %d

                project:
                  name: purchase-service
                  group: com.example
                  artifact: purchase-service
                  package: com.example.purchase

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
}
