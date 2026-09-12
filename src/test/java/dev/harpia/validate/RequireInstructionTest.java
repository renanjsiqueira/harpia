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

/** A positive Flow precondition with an explicit, declared domain failure. */
class RequireInstructionTest {

    private static final String SERVICE =
            "src/main/java/com/example/wallet/service/PaymentService.java";

    @TempDir
    Path projectRoot;

    @Test
    void aRequirementContinuesWhenTrueAndRaisesTheDeclaredErrorWhenFalse(
            @TempDir Path classes) throws IOException {
        project(1, "require amount <= balance otherwise insufficient balance", """
                - invalid input -> 400
                - insufficient balance -> 422
                """);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("Flow Require");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Step Require amount <= balance otherwise insufficient balance");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction REQUIRE amount <= balance otherwise insufficient balance");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .contains("if (!(request.amount().compareTo(request.balance()) <= 0)) {")
                .contains("throw new InsufficientBalanceException(\"amount <= balance\");");
        assertThat(files.get(SERVICE).indexOf("if (!(request.amount()"))
                .isLessThan(files.get(SERVICE).indexOf("new Payment()"));
        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aRequirementCannotRaiseAnUndeclaredError() throws IOException {
        project(1, "require amount <= balance otherwise insufficient balance",
                "- invalid input -> 400\n");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FAIL_UNDECLARED))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("raises 'insufficient balance'")
                        .contains("does not declare in '### Errors'"));
    }

    @Test
    void aRequirementMustBeBoolean() throws IOException {
        project(1, "require amount + balance otherwise insufficient balance", """
                - invalid input -> 400
                - insufficient balance -> 422
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_LOGIC_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("precondition 'amount + balance' must be Boolean but is Decimal"));
    }

    @Test
    void otherwiseIsRequiredAndNamesAnErrorRatherThanAStatus() throws IOException {
        project(1, "require amount <= balance", "- insufficient balance -> 422\n");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_FLOW_COMMAND))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("unknown flow command"));
    }

    @Test
    void versionZeroDoesNotSilentlyAcquireVersionOneRequirements() throws IOException {
        project(0, "require amount <= balance otherwise insufficient balance",
                "- insufficient balance -> 422\n");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SYNTAX_DECLARATION_TOO_NEW))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("'require' in a flow needs harpia.languageVersion 1"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(int languageVersion, String requirement, String errors)
            throws IOException {
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
                """ + requirement + """

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
                  languageVersion: %d

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
                """.formatted(languageVersion), StandardCharsets.UTF_8);
    }
}
