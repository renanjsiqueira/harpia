package dev.harpia.parse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpecParserTest {

    @Test
    void parsesTheCanonicalCustomerCrudFixture() {
        Path root = Path.of("").toAbsolutePath().normalize();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        SourceFile source = SourceFile.read(
                        root,
                        root.resolve("examples/customer/specs/customer.harpia.md"),
                        diagnostics)
                .orElseThrow();

        SpecAst spec = SpecParser.parse(source, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(spec.entityName()).isEqualTo("Customer");
        assertThat(spec.useCases())
                .extracting(SpecAst.UseCaseDeclaration::title)
                .containsExactly(
                        "Create Customer",
                        "Get Customer",
                        "List Customers",
                        "Update Customer",
                        "Delete Customer");
        assertThat(spec.useCases())
                .flatExtracting(SpecAst.UseCaseDeclaration::flow)
                .extracting(statement -> statement.getClass().getSimpleName())
                .contains(
                        "ValidateInput",
                        "CreateFrom",
                        "LoadById",
                        "UpdateFrom",
                        "ListAll",
                        "Save",
                        "Delete",
                        "Return");
    }

    @Test
    void parsesACompleteSpecificationWithoutInterpretingDocumentation() {
        SourceFile source = new SourceFile("specs/customer.harpia.md", """
                # Customer

                Human-readable documentation has no executable meaning.

                ## Data

                - id: UUID generated
                - name: String required
                - email: Email required unique
                - active: Boolean required default true

                ## Create Customer

                ### Endpoint

                POST /customers

                ### Access

                public

                ### Input

                - name: String required
                - email: Email required

                ### Rules

                This paragraph remains documentation in V0.

                ### Flow

                ```flow
                validate input

                customer = create Customer from input
                save customer
                return customer
                ```

                ### Output

                201 Customer

                ### Errors

                - invalid input -> 400
                - duplicate email -> 409
                """);
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        SpecAst spec = SpecParser.parse(source, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(spec.file()).isEqualTo("specs/customer.harpia.md");
        assertThat(spec.entityName()).isEqualTo("Customer");
        assertThat(spec.fields()).hasSize(4);
        assertThat(spec.fields().get(2).name()).isEqualTo("email");
        assertThat(spec.fields().get(2).unique()).isTrue();
        assertThat(spec.fields().get(3).defaultValue()).contains("true");
        assertThat(spec.useCases()).singleElement().satisfies(useCase -> {
            assertThat(useCase.title()).isEqualTo("Create Customer");
            assertThat(useCase.endpoint().method()).isEqualTo("POST");
            assertThat(useCase.input()).hasSize(2);
            assertThat(useCase.flow())
                    .extracting(statement -> statement.getClass().getSimpleName())
                    .containsExactly(
                            "ValidateInput", "CreateFrom", "Save", "Return");
            assertThat(useCase.errors()).hasSize(2);
        });
    }

    @Test
    void parsesUseCaseSectionsIndependentlyOfTheirOrder() {
        SourceFile source = new SourceFile("specs/customer.harpia.md", """
                # Customer

                ## Data

                - id: UUID generated

                ## Get Customer

                ### Output

                200 Customer

                ### Flow

                ```flow
                customer = load Customer by id
                return customer
                ```

                ### Access

                public

                ### Endpoint

                GET /customers/{id}
                """);
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        SpecAst spec = SpecParser.parse(source, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(spec.useCases()).singleElement().satisfies(useCase -> {
            assertThat(useCase.endpoint().path()).isEqualTo("/customers/{id}");
            assertThat(useCase.output().status()).isEqualTo(200);
            assertThat(useCase.flow()).hasSize(2);
        });
    }
}
