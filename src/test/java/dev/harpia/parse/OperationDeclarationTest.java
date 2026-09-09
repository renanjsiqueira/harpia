package dev.harpia.parse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LanguageVersion;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.source.SourceFile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * An operation should say what it is. Until V1 the only way to know whether a use case wrote or
 * read was to look at the shape of its flow, which meant the specification never actually said it.
 */
class OperationDeclarationTest {

    @Test
    void aCommandAndAQueryAreDeclaredKindsInV1() {
        ModuleAst module = parse(LanguageVersion.V1, module("""
                ## Command Register Customer
                """ + sections("POST /customers", """
                validate input
                customer = create Customer from input
                save customer
                return customer
                """, "201 Customer") + """

                ## Query List Customers
                """ + sections("GET /customers", """
                customers = list Customer
                return customers
                """, "200 List<Customer>")));

        assertThat(module.declarations())
                .filteredOn(declaration -> declaration.kind().isOperation())
                .extracting(DeclarationAst::kind)
                .containsExactly(DeclarationKind.COMMAND, DeclarationKind.QUERY);
    }

    @Test
    void thePrefixIsNotPartOfTheOperationName() {
        ModuleAst module = parse(LanguageVersion.V1, module("""
                ## Command Register Customer
                """ + sections("POST /customers", """
                validate input
                customer = create Customer from input
                save customer
                return customer
                """, "201 Customer")));

        assertThat(module.declarations())
                .filteredOn(declaration -> declaration.kind() == DeclarationKind.COMMAND)
                .singleElement()
                .satisfies(declaration ->
                        assertThat(declaration.declaredName()).isEqualTo("Register Customer"));
    }

    @Test
    void v0RefusesADeclarationOnlyALaterVersionUnderstands() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        Optional<ModuleAst> module = SpecParser.parse(
                new SourceFile("specs/customer.harpia.md", module("""
                        ## Command Register Customer
                        """ + sections("POST /customers", """
                        validate input
                        customer = create Customer from input
                        save customer
                        return customer
                        """, "201 Customer"))),
                LanguageVersion.V0,
                diagnostics);

        assertThat(module).isEmpty();
        assertThat(diagnostics.diagnostics())
                .as("reading it as a use case named CommandRegisterCustomer would be silent")
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SYNTAX_DECLARATION_TOO_NEW))
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.message())
                            .contains("declares a Command")
                            .contains("harpia.languageVersion 1");
                    assertThat(diagnostic.hint()).hasValueSatisfying(hint ->
                            assertThat(hint).contains("set harpia.languageVersion to 1"));
                });
    }

    @Test
    void anUnprefixedHeadingStillWorksInV1() {
        ModuleAst module = parse(LanguageVersion.V1, module("""
                ## List Customers
                """ + sections("GET /customers", """
                customers = list Customer
                return customers
                """, "200 List<Customer>")));

        assertThat(module.declarations())
                .filteredOn(declaration -> declaration.kind().isOperation())
                .extracting(DeclarationAst::kind)
                .as("nothing already written changes meaning")
                .containsExactly(DeclarationKind.USE_CASE);
    }

    @Test
    void declaredOperationsMayOmitTheirInlineHttpBindingInV1() {
        ModuleAst module = parse(LanguageVersion.V1, module("""
                ## Query List Customers

                ### Flow

                ```flow
                customers = list Customer
                return customers
                ```

                ### Output

                200 List<Customer>
                """));

        assertThat(module.useCases()).singleElement().satisfies(operation -> {
            assertThat(operation.declaredKind()).isEqualTo(DeclarationKind.QUERY);
            assertThat(operation.endpoint()).isEmpty();
            assertThat(operation.access()).isEmpty();
        });
    }

    @Test
    void anInlineHttpBindingMustDeclareEndpointAndAccessTogether() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        SpecParser.parse(
                new SourceFile("specs/customer.harpia.md", module("""
                        ## Command Register Customer

                        ### Endpoint

                        POST /customers

                        ### Flow

                        ```flow
                        customer = create Customer from input
                        save customer
                        return customer
                        ```

                        ### Output

                        201 Customer
                        """)),
                LanguageVersion.V1,
                diagnostics);

        assertThat(diagnostics.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_USE_CASE_SECTION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("inline HTTP binding")
                        .contains("### Access"));
    }

    @Test
    void aLegacyUseCaseStillRequiresItsInlineHttpBinding() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        SpecParser.parse(
                new SourceFile("specs/customer.harpia.md", module("""
                        ## List Customers

                        ### Flow

                        ```flow
                        customers = list Customer
                        return customers
                        ```

                        ### Output

                        200 List<Customer>
                        """)),
                LanguageVersion.V1,
                diagnostics);

        assertThat(diagnostics.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_USE_CASE_SECTION))
                .extracting(Diagnostic::message)
                .containsExactlyInAnyOrder(
                        "use-case 'List Customers' is missing '### Endpoint'",
                        "use-case 'List Customers' is missing '### Access'");
    }

    private static ModuleAst parse(LanguageVersion version, String source) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        Optional<ModuleAst> module = SpecParser.parse(
                new SourceFile("specs/customer.harpia.md", source), version, diagnostics);
        assertThat(diagnostics.diagnostics())
                .as("fixture must parse cleanly")
                .isEmpty();
        return module.orElseThrow();
    }

    private static String module(String declarations) {
        return """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                """ + declarations;
    }

    private static String sections(String endpoint, String flow, String output) {
        return """

                ### Endpoint

                %s

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                %s```

                ### Output

                %s
                """.formatted(endpoint, flow, output);
    }
}
