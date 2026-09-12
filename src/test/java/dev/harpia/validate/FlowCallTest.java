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

/** Typed calls make Flow orchestrate named computations without absorbing their algorithms. */
class FlowCallTest {

    private static final String SERVICE =
            "src/main/java/com/example/purchase/service/PurchaseService.java";

    @TempDir
    Path projectRoot;

    @Test
    void aNamedLogicCallCrossesEveryIrAndGeneratesCompilableJava(@TempDir Path classes)
            throws IOException {
        project(1, """
                discount = call CalculateDiscount(vip = vip, total = total)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "");

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("Call");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Step Call discount = CalculateDiscount(total: Decimal, vip: Boolean)");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction CALL_LOGIC discount = CalculateDiscount("
                        + "total: Decimal, vip: Boolean) -> Decimal");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .contains("import com.example.purchase.logic.CalculateDiscount;")
                .contains("BigDecimal discount = CalculateDiscount.apply("
                        + "request.total(), request.vip());");
        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aReadableMultilineCallHasTheSameTypedMeaning() throws IOException {
        project(1, """
                discount = call CalculateDiscount(
                    total = total,
                    vip = vip
                )
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "");

        assertThat(compile().diagnostics()).isEmpty();
        assertThat(Inspector.render(compile(), Stage.APPLICATION_IR).orElseThrow())
                .contains("discount = CalculateDiscount(total: Decimal, vip: Boolean) -> Decimal");
    }

    @Test
    void callArgumentsAreCheckedByNameAndCompleteness() throws IOException {
        project(1, """
                discount = call CalculateDiscount(total = total, total = total, other = vip)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_FLOW_CALL_ARGUMENT))
                .hasSize(3)
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("repeats argument 'total'"))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("has no parameter 'other'"))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("missing argument 'vip'"));
    }

    @Test
    void aLogicResultMustBeAssigned() throws IOException {
        project(1, """
                call CalculateDiscount(total = total, vip = vip)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_FLOW_CALL_RESULT))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("returns a value")
                        .contains("assign it"));
    }

    @Test
    void anUnknownTargetIsNotDeferredToGeneratedJava() throws IOException {
        project(1, """
                discount = call MissingLogic(total = total, vip = vip)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_FLOW_CALL_TARGET))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("unknown Flow call target 'MissingLogic'"));
    }

    @Test
    void aCustomLogicContractIsInjectedAsTheTargetAdapter(@TempDir Path classes)
            throws IOException {
        project(1, """
                discount = call CalculateDiscount(total = total, vip = vip)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, """
                ### Implementation

                custom DiscountCalculator
                """);

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("CalculateDiscount(total: Decimal, vip: Boolean) "
                        + "custom DiscountCalculator");
        assertThat(result.tree().orElseThrow().files().get(SERVICE))
                .contains("private final DiscountCalculator discountCalculator;")
                .contains("BigDecimal discount = discountCalculator.apply("
                        + "request.total(), request.vip());");
        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }

    @Test
    void aPriorScalarResultCanFeedTheNextCall(@TempDir Path classes) throws IOException {
        project(1, """
                discount = call CalculateDiscount(total = total, vip = vip)
                adjusted = call CalculateDiscount(total = discount, vip = vip)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "");

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files().get(SERVICE))
                .contains("BigDecimal adjusted = CalculateDiscount.apply("
                        + "discount, request.vip());");
        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }

    @Test
    void aCommandCanBeCalledWithNamedTypedArguments(@TempDir Path classes)
            throws IOException {
        project(1, """
                discount = call CalculateDiscount(total = total, vip = vip)
                call RecordAudit(total = discount, vip = vip)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "");

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Step OperationCall RecordAudit(total: Decimal, vip: Boolean) "
                        + "-> NOTHING");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction CALL_OPERATION RecordAudit("
                        + "total: Decimal, vip: Boolean) -> NOTHING");
        assertThat(result.tree().orElseThrow().files().get(SERVICE))
                .contains("this.recordAudit(new RecordAuditRequest(discount, request.vip()));");
        GeneratedJava.compiles(result.tree().orElseThrow().files(), classes);
    }

    @Test
    void versionZeroDoesNotSilentlyAcquireCalls() throws IOException {
        project(0, """
                discount = call CalculateDiscount(total = total, vip = vip)
                purchase = create Purchase from input
                save purchase
                return purchase
                """, "", false);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SYNTAX_DECLARATION_TOO_NEW))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("'call' in a flow needs harpia.languageVersion 1"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(int version, String flow, String implementation) throws IOException {
        project(version, flow, implementation, true);
    }

    private void project(
            int version, String flow, String implementation, boolean declaresLogic)
            throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        String scenario = declaresLogic && !implementation.contains("custom ") ? """

                ## Scenario Base discount

                ### Given

                - total: 10
                - vip: false

                ### When

                CalculateDiscount

                ### Then

                - result: 10
                """ : "";
        String logic = declaresLogic ? """

                ## Logic CalculateDiscount

                ### Input

                - total: Decimal
                - vip: Boolean

                ### Output

                Decimal

                %s
                %s
                """.formatted(implementation.isBlank() ? """
                ```logic
                return total
                ```
                """ : implementation, scenario) : "";
        String heading = version == 0 ? "Place Purchase" : "Command PlacePurchase";
        // A Command heading is V1-only, so under V0 declaring one would raise a second HRP1107 and
        // hide the refusal this fixture exists to show.
        String audit = version == 0 ? "" : """

                ## Command RecordAudit

                ### Input

                - total: Decimal required
                - vip: Boolean required

                ### Flow

                ```flow
                return nothing
                ```

                ### Output

                204 nothing
                """;
        Files.writeString(projectRoot.resolve("specs/purchase.harpia.md"), """
                # Purchase

                ## Data

                - id: UUID generated
                - total: Decimal required
                - vip: Boolean required
                %s
                %s

                ## %s

                ### Input

                - total: Decimal required
                - vip: Boolean required

                ### Flow

                ```flow
                %s```

                ### Output

                201 Purchase
                """.formatted(logic, audit, heading, flow), StandardCharsets.UTF_8);
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
                """.formatted(version), StandardCharsets.UTF_8);
    }
}
