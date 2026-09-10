package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Java contract a declared event becomes.
 *
 * <p>A record, because an event is a fact: it happened, and nothing that happens can later be
 * edited. Nothing here publishes or subscribes — the contract exists whether or not a provider
 * ever carries it anywhere.
 */
class EventContractTest {

    private static final String EVENT =
            "src/main/java/com/example/purchase/event/PurchasePlaced.java";

    @TempDir
    Path projectRoot;

    @Test
    void anEventBecomesAnImmutableRecordInItsOwnPackage() throws IOException {
        project();

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files().get(EVENT))
                .contains("package com.example.purchase.event;")
                .contains("public record PurchasePlaced(")
                .contains("        UUID purchase,")
                .contains("        Status status,")
                .contains("        BigDecimal total)")
                .as("a reference is the identity it points at, not the row")
                .doesNotContain("Purchase purchase")
                .as("no provider is chosen here")
                .doesNotContain("ApplicationEventPublisher", "@EventListener", "KafkaTemplate");
    }

    @Test
    void theEventPackageIsSeparateFromTheDomainAndTheDtos() throws IOException {
        project();

        assertThat(compile().tree().orElseThrow().files().keySet())
                .contains(EVENT)
                .doesNotContain(
                        "src/main/java/com/example/purchase/domain/PurchasePlaced.java",
                        "src/main/java/com/example/purchase/dto/PurchasePlaced.java");
    }

    @Test
    void aProjectWithoutEventsGeneratesNoEventPackage() throws IOException {
        project("");

        assertThat(compile().tree().orElseThrow().files().keySet())
                .noneMatch(path -> path.contains("/event/"));
    }

    @Test
    void theGeneratedProjectCompilesWithItsEventContracts(@TempDir Path classes)
            throws IOException {
        project();

        GeneratedJava.compiles(compile().tree().orElseThrow().files(), classes);
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project() throws IOException {
        project("""
                ## Event PurchasePlaced

                ### Payload

                - purchase: Reference<Purchase> required
                - status: Status required
                - total: Decimal required
                """);
    }

    private void project(String event) throws IOException {
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
                ## Query Get Purchase

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
                  languageVersion: 1

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
                """, StandardCharsets.UTF_8);
    }
}
