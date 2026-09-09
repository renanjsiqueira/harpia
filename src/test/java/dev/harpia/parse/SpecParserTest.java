package dev.harpia.parse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LanguageVersion;
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

        ModuleAst module = SpecParser.parse(source, LanguageVersion.V0, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(module.entity().name()).isEqualTo("Customer");
        assertThat(module.useCases())
                .extracting(SpecAst.UseCaseDeclaration::title)
                .containsExactly(
                        "Create Customer",
                        "Get Customer",
                        "List Customers",
                        "Update Customer",
                        "Delete Customer");
        assertThat(module.useCases())
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

        ModuleAst module = SpecParser.parse(source, LanguageVersion.V0, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(module.file()).isEqualTo("specs/customer.harpia.md");
        assertThat(module.entity().name()).isEqualTo("Customer");
        assertThat(module.entity().fields()).hasSize(4);
        assertThat(module.entity().fields().get(2).name()).isEqualTo("email");
        assertThat(module.entity().fields().get(2).unique()).isTrue();
        assertThat(module.entity().fields().get(3).defaultValue()).contains("true");
        assertThat(module.useCases()).singleElement().satisfies(useCase -> {
            assertThat(useCase.title()).isEqualTo("Create Customer");
            assertThat(useCase.endpoint()).get().extracting(SpecAst.Endpoint::method)
                    .isEqualTo("POST");
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

        ModuleAst module = SpecParser.parse(source, LanguageVersion.V0, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(module.useCases()).singleElement().satisfies(useCase -> {
            assertThat(useCase.endpoint()).get().extracting(SpecAst.Endpoint::path)
                    .isEqualTo("/customers/{id}");
            assertThat(useCase.output().status()).isEqualTo(200);
            assertThat(useCase.flow()).hasSize(2);
        });
    }

    @Test
    void preservesDeclarationOrderInTheModuleAstWhileProvidingTypedViews() {
        SourceFile source = new SourceFile("specs/mixed.harpia.md", """
                # Mixed

                ## Logic Constant

                ### Input

                ### Output

                Int

                ```logic
                return 1
                ```

                ## Data

                - id: UUID generated
                """);
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        ModuleAst module = SpecParser.parse(source, LanguageVersion.V0, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(module.declarations())
                .extracting(DeclarationAst::kind)
                .containsExactly(DeclarationKind.LOGIC, DeclarationKind.ENTITY);
        assertThat(module.logics()).singleElement()
                .extracting(LogicAst.Declaration::name)
                .isEqualTo("Constant");
        assertThat(module.entities()).singleElement()
                .extracting(SpecAst.EntityDeclaration::name)
                .isEqualTo("Mixed");
    }
}
