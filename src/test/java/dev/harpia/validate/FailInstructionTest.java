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
 * The instruction that raises a declared domain error.
 *
 * <p>{@code CMD-004} made the error a type with a status and generated the class; nothing threw it.
 * A bare {@code fail} would fire on every run and end the operation, so the guard is what makes it
 * an instruction rather than a dead end.
 */
class FailInstructionTest {

    private static final String SERVICE =
            "src/main/java/com/example/wallet/service/PaymentService.java";

    @TempDir
    Path projectRoot;

    @Test
    void aGuardedFailRaisesTheDeclaredError(@TempDir Path classes) throws IOException {
        project("fail insufficient balance when amount > balance", """
                - invalid input -> 400
                - insufficient balance -> 422
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction FAIL insufficient balance when amount > balance");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .as("the guard reads the request, the same scope a rule sees")
                .contains("if (request.amount().compareTo(request.balance()) > 0) {")
                .contains("throw new InsufficientBalanceException(\"amount > balance\");");
        assertThat(files.get(SERVICE).indexOf("InsufficientBalanceException"))
                .as("raised where the flow put it, before the entity is built")
                .isLessThan(files.get(SERVICE).indexOf("new Payment()"));

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aFailCannotRaiseAnErrorTheOperationNeverDeclared() throws IOException {
        project("fail insufficient balance when amount > balance", "- invalid input -> 400\n");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FAIL_UNDECLARED))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("raises 'insufficient balance'")
                        .contains("does not declare in '### Errors'"));
    }

    @Test
    void aGuardMustBeBoolean() throws IOException {
        project("fail insufficient balance when amount + balance", """
                - invalid input -> 400
                - insufficient balance -> 422
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_LOGIC_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("must be Boolean but is Decimal"));
    }

    @Test
    void aGuardCannotNameSomethingOutsideTheInput() throws IOException {
        project("fail insufficient balance when amount > limit", """
                - invalid input -> 400
                - insufficient balance -> 422
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME))
                .singleElement()
                .satisfies(diagnostic ->
                        assertThat(diagnostic.message()).contains("unknown value 'limit'"));
    }

    @Test
    void aFailWithoutAGuardIsNotAFlowCommand() throws IOException {
        project("fail insufficient balance", """
                - invalid input -> 400
                - insufficient balance -> 422
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_FLOW_COMMAND))
                .singleElement()
                .satisfies(diagnostic ->
                        assertThat(diagnostic.message()).contains("unknown flow command"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String failLine, String errors) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/payment.harpia.md"), """
                # Payment

                ## Data

                - id: UUID generated
                - amount: Decimal required
                - balance: Decimal required

                ## Create Payment

                ### Endpoint

                POST /payments

                ### Access

                public

                ### Input

                - amount: Decimal required
                - balance: Decimal required

                ### Flow

                ```flow
                validate input
                """ + failLine + """

                payment = create Payment from input
                save payment
                return payment
                ```

                ### Output

                201 Payment

                ### Errors

                """ + errors, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: wallet-service
                  group: com.example
                  artifact: wallet-service
                  package: com.example.wallet

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
