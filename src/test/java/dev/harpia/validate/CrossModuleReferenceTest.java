package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An operation belongs to the entity its flow names, not to the file it was typed in. In V0 those
 * coincide; from V1 an operation may live in a module of its own.
 */
class CrossModuleReferenceTest {

    @TempDir
    Path projectRoot;

    private static final String CUSTOMER = """
            # Customer

            ## Data

            - id: UUID generated
            - name: String required
            """;

    private static final String REGISTRATION = """
            # Registration

            ## Command Register Customer

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

    @Test
    void anOperationAttachesToTheEntityItNamesEvenFromAnotherModule() throws IOException {
        project(1, Map.of("customer", CUSTOMER, "registration", REGISTRATION));

        CompileResult result = compile();

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR)).hasValueSatisfying(business ->
                assertThat(business)
                        .contains("Entity Customer")
                        .contains("  Command RegisterCustomer"));
    }

    @Test
    void aNominalTypeDeclaredInOneModuleIsAFieldTypeInAnother() throws IOException {
        project(1, Map.of(
                "00-types", """
                        # Catalog

                        ## Enum Status

                        - active
                        - blocked

                        ## Value Address

                        - street: String required
                        - city: String required
                        """,
                "01-customer", """
                        # Customer

                        ## Data

                        - id: UUID generated
                        - status: Status required
                        - address: Address required

                        ## Query List Customers

                        ### Endpoint

                        GET /customers

                        ### Access

                        public

                        ### Flow

                        ```flow
                        customers = list Customer
                        return customers
                        ```

                        ### Output

                        200 List<Customer>
                        """));

        // The symbol table is one table for the project, so where a type was written down is not
        // part of what it means.
        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Column status: Status required")
                .contains("Column address: Address required");
    }

    @Test
    void aReferenceCrossesModulesTheSameWay() throws IOException {
        project(1, Map.of(
                "00-customer", """
                        # Customer

                        ## Data

                        - id: UUID generated
                        - name: String required

                        ## Query List Customers

                        ### Endpoint

                        GET /customers

                        ### Access

                        public

                        ### Flow

                        ```flow
                        customers = list Customer
                        return customers
                        ```

                        ### Output

                        200 List<Customer>
                        """,
                "01-invoice", """
                        # Invoice

                        ## Data

                        - id: UUID generated
                        - customer: Reference<Customer> required

                        ## Query List Invoices

                        ### Endpoint

                        GET /invoices

                        ### Access

                        public

                        ### Flow

                        ```flow
                        invoices = list Invoice
                        return invoices
                        ```

                        ### Output

                        200 List<Invoice>
                        """));

        assertThat(compile().diagnostics()).isEmpty();
    }

    @Test
    void aNominalTypeNobodyDeclaresIsStillRefused() throws IOException {
        project(1, Map.of(
                "00-customer", """
                        # Customer

                        ## Data

                        - id: UUID generated
                        - status: Status required

                        ## Query List Customers

                        ### Endpoint

                        GET /customers

                        ### Access

                        public

                        ### Flow

                        ```flow
                        customers = list Customer
                        return customers
                        ```

                        ### Output

                        200 List<Customer>
                        """));

        // One table for the project also means a name nobody wrote down is missing everywhere,
        // not merely missing here.
        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_UNKNOWN_TYPE)
                                || diagnostic.code().equals(ErrorCodes.SYNTAX_UNKNOWN_TYPE))
                .isNotEmpty();
    }

    @Test
    void theSymbolKeepsTheModuleThatDeclaredIt() throws IOException {
        project(1, Map.of("customer", CUSTOMER, "registration", REGISTRATION));

        assertThat(Inspector.render(compile(), Stage.SYMBOLS)).hasValueSatisfying(symbols ->
                assertThat(symbols)
                        .contains("Customer  [specs/customer.harpia.md]")
                        .contains("RegisterCustomer  [specs/registration.harpia.md]"));
    }

    @Test
    void aModuleWithoutDataMayStillDeclareOperationsFromV1() throws IOException {
        project(1, Map.of("customer", CUSTOMER, "registration", REGISTRATION));

        assertThat(compile().hasErrors()).isFalse();
    }

    @Test
    void v0StillRequiresTheOperationToLiveWithItsEntity() throws IOException {
        project(0, Map.of("customer", CUSTOMER, "registration",
                REGISTRATION.replace("## Command Register Customer", "## Register Customer")));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SYNTAX_DATA_SECTION))
                .isNotEmpty();
    }

    @Test
    void anEntityNobodyDeclaresIsStillRefused() throws IOException {
        project(1, Map.of("registration", REGISTRATION));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FOREIGN_ENTITY))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("entity 'Customer' is not declared"));
    }

    @Test
    void anOperationWorkingOnTwoEntitiesIsRefused() throws IOException {
        project(1, Map.of(
                "customer", CUSTOMER,
                "account", CUSTOMER.replace("Customer", "Account"),
                "mixed", """
                        # Mixed

                        ## Query Mix Things

                        ### Endpoint

                        GET /mixed

                        ### Access

                        public

                        ### Flow

                        ```flow
                        customers = list Customer
                        accounts = list Account
                        return customers
                        ```

                        ### Output

                        200 List<Customer>
                        """));

        assertThat(compile().diagnostics())
                .extracting(Diagnostic::message)
                .anySatisfy(message -> assertThat(message)
                        .contains("works on more than one entity"));
    }

    @Test
    void anEntityNoOperationTouchesIsStillReportedAsOrphaned() throws IOException {
        project(1, Map.of("customer", CUSTOMER));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_ORPHAN_ENTITY))
                .singleElement()
                .satisfies(diagnostic ->
                        assertThat(diagnostic.message()).contains("has no use cases"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE));
    }

    private void project(int languageVersion, Map<String, String> modules) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        for (Map.Entry<String, String> module : modules.entrySet()) {
            Files.writeString(
                    projectRoot.resolve("specs/" + module.getKey() + ".harpia.md"),
                    module.getValue(),
                    StandardCharsets.UTF_8);
        }
        Files.writeString(
                projectRoot.resolve("harpia.yaml"),
                """
                harpia:
                  schemaVersion: 1
                  languageVersion: %d

                project:
                  name: customer-service
                  group: com.example
                  artifact: customer-service
                  package: com.example.customer

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
                """.formatted(languageVersion),
                StandardCharsets.UTF_8);
    }
}
