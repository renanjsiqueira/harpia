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

/** Where a value can be named, and where a diagnostic says the wrong name is. */
class FlowScopeTest {

    @TempDir
    Path projectRoot;

    /**
     * C11 — a name used too early and a name used outside its block, each reported at the word.
     *
     * <p>The column is the point of this test. A flow line holds several names, and a diagnostic
     * that reports the start of the line leaves the reader to find which one it meant — in an
     * editor, it underlines {@code set} instead of the name that does not exist. So the assertion
     * is not "an error happened here somewhere": it is the exact offset of the reference inside
     * the line the fixture wrote.
     */
    @Test
    void invalidReferencesPointToTheirExactSourceRange() throws IOException {
        project("""
                validate input
                order = create SalesOrder from input
                set order.total = discount
                discount = call CalculateDiscount(total = total)
                if vip
                    inner = call CalculateDiscount(total = total)
                set order.total = inner
                save order
                return order
                """);

        List<Diagnostic> diagnostics = compile().diagnostics();

        assertThat(scopeErrorAt("set order.total = discount", "discount", diagnostics))
                .as("a value named before the call that produces it is not in scope yet")
                .isNotNull();
        assertThat(scopeErrorAt("set order.total = inner", "inner", diagnostics))
                .as("a value produced inside a branch does not outlive the branch")
                .isNotNull();
    }

    /** The diagnostic for {@code name} in {@code line}, or null, matched by exact position. */
    private Diagnostic scopeErrorAt(String line, String name, List<Diagnostic> diagnostics)
            throws IOException {
        List<String> lines = Files.readAllLines(
                projectRoot.resolve("specs/order.harpia.md"), StandardCharsets.UTF_8);
        int index = lines.indexOf(line);
        assertThat(index).as("the fixture writes '%s'", line).isNotNegative();
        int column = line.indexOf(name) + 1;
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_LOGIC_UNKNOWN_NAME))
                .filter(diagnostic -> diagnostic.message().contains("'" + name + "'"))
                .filter(diagnostic -> diagnostic.where()
                        .filter(where -> where.line() == index + 1 && where.column() == column)
                        .isPresent())
                .findFirst()
                .orElse(null);
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE));
    }

    private void project(String flow) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # SalesOrder

                ## Data

                - id: UUID generated
                - orderNumber: String required
                - total: Decimal required default 0
                - vip: Boolean required default false

                ## Logic CalculateDiscount

                ### Input

                - total: Decimal

                ### Output

                Decimal

                ```logic
                return total * 0.10
                ```

                ## Scenario Ten percent

                ### Given

                - total: 100.00

                ### When

                CalculateDiscount

                ### Then

                - result: 10.0000

                ## Command PlaceOrder

                ### Input

                - orderNumber: String required
                - total: Decimal required
                - vip: Boolean required

                ### Flow

                ```flow
                %s```

                ### Output

                201 SalesOrder
                """.formatted(flow), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: commerce-service
                  group: com.example
                  artifact: commerce-service
                  package: com.example.commerce

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
