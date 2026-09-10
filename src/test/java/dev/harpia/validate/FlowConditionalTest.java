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

/** Branching in an application flow without turning the flow into a second programming language. */
class FlowConditionalTest {

    private static final String SERVICE =
            "src/main/java/com/example/order/service/PurchaseService.java";

    @TempDir
    Path projectRoot;

    @Test
    void ifElseBranchesGenerateTypedJavaAtTheDeclaredPosition(@TempDir Path classes)
            throws IOException {
        project(1, "Command PrioritizeOrder", """
                validate input
                purchase = create Purchase from input
                if premium
                    set purchase.status = "PRIORITY"
                else
                    set purchase.status = "STANDARD"
                save purchase
                return purchase
                """);

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .containsSubsequence(
                        "Step Conditional premium",
                        "Then",
                        "Step SetField",
                        "Else",
                        "Step SetField");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .containsSubsequence(
                        "Instruction IF premium",
                        "Then",
                        "Instruction SET_FIELD status = \"PRIORITY\"",
                        "Else",
                        "Instruction SET_FIELD status = \"STANDARD\"");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .contains("if (request.premium()) {")
                .contains("purchase.setStatus(\"PRIORITY\");")
                .contains("} else {")
                .contains("purchase.setStatus(\"STANDARD\");");
        assertThat(files.get(SERVICE).indexOf("if (request.premium())"))
                .isLessThan(files.get(SERVICE).indexOf("repository.save(purchase)"));
        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aConditionalMayNestTwice() throws IOException {
        project(1, "Command PrioritizeOrder", """
                purchase = create Purchase from input
                if premium
                    if status == "DRAFT"
                        set purchase.status = "PRIORITY"
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics()).isEmpty();
    }

    @Test
    void aThirdConditionalLevelIsRejected() throws IOException {
        project(1, "Command PrioritizeOrder", """
                purchase = create Purchase from input
                if premium
                    if premium
                        if premium
                            set purchase.status = "PRIORITY"
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_FLOW_COMMAND))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("nests at most 2 levels"));
    }

    @Test
    void aConditionMustBeBoolean() throws IOException {
        project(1, "Command PrioritizeOrder", """
                purchase = create Purchase from input
                if status
                    set purchase.status = "PRIORITY"
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_LOGIC_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("must be Boolean but is String"));
    }

    @Test
    void aQueryCannotHideAMutationInsideABranch() throws IOException {
        project(1, "Query InspectPurchase", "GET /purchases/{id}", """
                purchase = load Purchase by id
                if premium
                    set purchase.status = "PRIORITY"
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_QUERY_MUTATES))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot 'set'"));
    }

    @Test
    void aBranchCannotDefineAVariableThatEscapesItsJavaScope() throws IOException {
        project(1, "Command PrioritizeOrder", """
                purchase = create Purchase from input
                if premium
                    other = create Purchase from input
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FLOW_BRANCH_SCOPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot define variable 'other'")
                        .contains("before 'if'"));
    }

    @Test
    void aBranchCannotReturnFromTheOperationsSingleExitFlow() throws IOException {
        project(1, "Command PrioritizeOrder", """
                purchase = create Purchase from input
                if premium
                    return purchase
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FLOW_BRANCH_SCOPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("one final top-level return"));
    }

    @Test
    void ifNeedsAnIndentedBody() throws IOException {
        project(1, "Command PrioritizeOrder", """
                purchase = create Purchase from input
                if premium
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_FLOW_COMMAND))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("must be followed by commands indented by four spaces"));
    }

    @Test
    void versionZeroDoesNotSilentlyAcquireVersionOneBranching() throws IOException {
        project(0, "Prioritize Order", """
                purchase = create Purchase from input
                if premium
                    set purchase.status = "PRIORITY"
                save purchase
                return purchase
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SYNTAX_DECLARATION_TOO_NEW))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("needs harpia.languageVersion 1"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(int languageVersion, String heading, String flow) throws IOException {
        project(languageVersion, heading, "POST /purchases", flow);
    }

    private void project(
            int languageVersion, String heading, String endpoint, String flow) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # Purchase

                ## Data

                - id: UUID generated
                - premium: Boolean required
                - status: String required

                ## %s

                ### Endpoint

                %s

                ### Access

                public

                ### Input

                - premium: Boolean required
                - status: String required

                ### Flow

                ```flow
                %s```

                ### Output

                201 Purchase
                """.formatted(heading, endpoint, flow), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: %d

                project:
                  name: order-service
                  group: com.example
                  artifact: order-service
                  package: com.example.order

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
