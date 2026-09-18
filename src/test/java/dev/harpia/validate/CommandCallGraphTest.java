package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.ErrorCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a Command may call, seen from above the single call it writes. */
class CommandCallGraphTest {

    @TempDir
    Path projectRoot;

    /**
     * C12 — a cycle of Commands is refused, and a chain that does not close is not.
     *
     * <p>Both halves matter. Refusing the cycle is the point, but a rule that refuses any Command
     * calling another would pass the first half and make composition impossible, so the acyclic
     * chain is asserted in the same test and with the same shape.
     */
    @Test
    void cyclesAreRejectedAndAnAcyclicChainIsAccepted() throws IOException {
        project(CALLS_BETA, ENDS);
        assertThat(compile().diagnostics())
                .as("A -> B -> C with no way back is a chain, not a cycle")
                .isEmpty();

        project(CALLS_BETA, """
                validate input
                gamma = create Gamma from input
                created = call CreateAlpha(name = name)
                save gamma
                return nothing
                """);
        List<Diagnostic> indirect = compile().diagnostics();
        assertThat(cycle(indirect, "CreateAlpha"))
                .as("A -> B -> C -> A closes, and the message names the way round")
                .contains("CreateBeta -> CreateGamma -> CreateAlpha");
        assertThat(cycle(indirect, "CreateBeta"))
                .as("every Command on the ring is on a cycle of its own")
                .isNotEmpty();

        project("""
                validate input
                alpha = create Alpha from input
                mine = call CreateAlpha(name = name)
                save alpha
                return alpha
                """, ENDS);
        assertThat(compile().diagnostics())
                .as("a Command that calls itself is refused where it is written")
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_FLOW_CALL_TARGET);
                    assertThat(diagnostic.message()).contains("cannot call itself");
                });
    }

    private static String cycle(List<Diagnostic> diagnostics, String command) {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_FLOW_CALL_TARGET))
                .map(Diagnostic::message)
                .filter(message -> message.startsWith("Command '" + command + "' calls itself"))
                .findFirst()
                .orElse("");
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE));
    }

    private static final String CALLS_BETA = """
            validate input
            alpha = create Alpha from input
            call CreateBeta(name = name)
            save alpha
            return alpha
            """;

    private static final String ENDS = """
            validate input
            gamma = create Gamma from input
            save gamma
            return nothing
            """;

    /**
     * Three Commands, one per entity, where the first and the last flow are the variables.
     *
     * <p>A -> B -> C exists in every variant, so the only difference between an accepted project
     * and a refused one is whether the last Command reaches back to the first.
     */
    private void project(String alphaFlow, String gammaFlow) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        write("alpha", "Alpha", "CreateAlpha", alphaFlow);
        write("beta", "Beta", "CreateBeta", """
                validate input
                beta = create Beta from input
                call CreateGamma(name = name)
                save beta
                return nothing
                """);
        write("gamma", "Gamma", "CreateGamma", gammaFlow);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: chain-service
                  group: com.example
                  artifact: chain-service
                  package: com.example.chain

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

    private void write(String file, String entity, String command, String flow) throws IOException {
        boolean answers = command.equals("CreateAlpha");
        Files.writeString(projectRoot.resolve("specs/" + file + ".harpia.md"), """
                # %s

                ## Data

                - id: UUID generated
                - name: String required

                ## Command %s

                ### Input

                - name: String required

                ### Flow

                ```flow
                %s```

                ### Output

                %s
                """.formatted(entity, command, flow, answers ? "201 " + entity : "204 nothing"),
                StandardCharsets.UTF_8);
    }
}
