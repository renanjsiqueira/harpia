package dev.harpia.symbol;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LanguageVersion;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.source.SourceFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One table decides what every name means. Before it existed each validator kept its own map, so a
 * name meant whatever the code checking it happened to believe.
 */
class SymbolTableTest {

    @Test
    void declaresEachKindInItsOwnNamespace() {
        SymbolTable symbols = declare(customer(), pricing());

        assertThat(symbols.in(Namespace.TYPES)).extracting(Symbol::name).containsExactly("Customer");
        assertThat(symbols.in(Namespace.OPERATIONS))
                .extracting(Symbol::name)
                .containsExactly("CreateCustomer");
        assertThat(symbols.in(Namespace.COMPUTATIONS))
                .extracting(Symbol::name)
                .containsExactly("Discount");
        assertThat(symbols.in(Namespace.TESTS))
                .extracting(Symbol::name)
                .containsExactly("Half off");
    }

    @Test
    void aNameMayRepeatAcrossNamespacesWithoutBecomingAmbiguous() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        SymbolTable symbols = declare(diagnostics, """
                # Discount

                ## Data

                - id: UUID generated

                ## Logic Discount

                ### Input

                ### Output

                Int

                ```logic
                return 1
                ```
                """);

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(symbols.entity("Discount")).isPresent();
        assertThat(symbols.computation("Discount")).isPresent();
    }

    @Test
    void aDuplicateIsReportedAtBothDeclarations() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        declare(diagnostics, customer(), customer().replace("specs", "specs"));

        assertThat(diagnostics.diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_DUPLICATE_ENTITY))
                .hasSize(2)
                .allSatisfy(diagnostic -> {
                    assertThat(diagnostic.message())
                            .as("the other place is data, not text pasted into the message")
                            .doesNotContain(".harpia.md:");
                    assertThat(diagnostic.related()).singleElement().satisfies(related -> {
                        assertThat(related.where().file()).endsWith(".harpia.md");
                        assertThat(related.message())
                                .isIn("first declared here", "also declared here");
                    });
                });
    }

    @Test
    void aComputationCarriesTheSignatureEveryCallIsCheckedAgainst() {
        Symbol.Computation discount =
                declare(pricing()).computation("Discount").orElseThrow();

        assertThat(discount.parameters())
                .extracting(parameter -> parameter.name() + ": " + parameter.type().display())
                .containsExactly("total: Decimal");
        assertThat(discount.returnType().display()).isEqualTo("Decimal");
        assertThat(discount.parameter("total")).isPresent();
        assertThat(discount.parameter("missing")).isEmpty();
    }

    @Test
    void everySymbolKnowsTheModuleThatDeclaresIt() {
        assertThat(declare(customer()).entity("Customer").orElseThrow().module())
                .isEqualTo("specs/module-1.harpia.md");
    }

    @Test
    void declarationOrderIsDiscoveryOrderAndDoesNotDependOnHashing() {
        SymbolTable first = declare(customer(), pricing());
        SymbolTable second = declare(customer(), pricing());

        assertThat(first.in(Namespace.OPERATIONS).stream().map(Symbol::name).toList())
                .isEqualTo(second.in(Namespace.OPERATIONS).stream().map(Symbol::name).toList());
    }

    @Test
    void anUnknownNameSimplyDoesNotResolve() {
        SymbolTable symbols = declare(customer());

        assertThat(symbols.entity("Nothing")).isEmpty();
        assertThat(symbols.computation("Customer"))
                .as("an entity is not a computation")
                .isEmpty();
    }

    private static SymbolTable declare(String... modules) {
        return declare(new DiagnosticCollector(), modules);
    }

    private static SymbolTable declare(DiagnosticCollector diagnostics, String... modules) {
        List<ModuleAst> parsed = new ArrayList<>();
        for (int index = 0; index < modules.length; index++) {
            SourceFile source =
                    new SourceFile("specs/module-" + (index + 1) + ".harpia.md", modules[index]);
            parsed.add(SpecParser.parse(source, LanguageVersion.V0, diagnostics).orElseThrow());
        }
        return SymbolTable.declare(new ProjectAst(LanguageVersion.V0, parsed), diagnostics);
    }

    private static String customer() {
        return """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## Create Customer

                ### Endpoint

                POST /customers

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                customer = create Customer from input
                save customer
                return customer
                ```

                ### Output

                201 Customer
                """;
    }

    private static String pricing() {
        return """
                # Pricing

                ## Logic Discount

                ### Input

                - total: Decimal

                ### Output

                Decimal

                ```logic
                return total * 0.10
                ```

                ## Scenario Half off

                ### Given

                - total: 100

                ### When

                Discount

                ### Then

                - result: 10.00
                """;
    }
}
