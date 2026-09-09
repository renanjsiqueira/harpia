package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LanguageVersion;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.Severity;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.symbol.SymbolTable;
import dev.harpia.source.SourceFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticValidatorTest {

    @Test
    void eachSemanticRuleHasItsStableDiagnosticCode() {
        List<SemanticCase> cases = List.of(
                new SemanticCase("duplicate entity", ErrorCodes.SEMANTIC_DUPLICATE_ENTITY, List.of(
                        validCrud("Customer", "/customers"),
                        validCrud("Customer", "/other-customers"))),
                new SemanticCase("duplicate field", ErrorCodes.SEMANTIC_DUPLICATE_FIELD, List.of(spec(
                        "Customer",
                        "- id: UUID generated\n- name: String\n- name: String",
                        listUseCase("Customer", "/customers")))),
                new SemanticCase("invalid id", ErrorCodes.SEMANTIC_ID_FIELD, List.of(spec(
                        "Customer", "- id: UUID", listUseCase("Customer", "/customers")))),
                new SemanticCase("invalid default", ErrorCodes.SEMANTIC_DEFAULT_TYPE, List.of(spec(
                        "Customer",
                        "- id: UUID generated\n- active: Boolean default yes",
                        listUseCase("Customer", "/customers")))),
                new SemanticCase("duplicate route", ErrorCodes.SEMANTIC_DUPLICATE_ROUTE, List.of(spec(
                        "Customer",
                        "- id: UUID generated",
                        listUseCase("Customer", "/customers")
                                + listUseCaseNamed("All Customers", "Customer", "/customers")))),
                new SemanticCase("path and flow", ErrorCodes.SEMANTIC_PATH_VAR_FLOW, List.of(spec(
                        "Customer",
                        "- id: UUID generated",
                        listUseCase("Customer", "/customers/{id}")))),
                new SemanticCase("undefined variable", ErrorCodes.SEMANTIC_UNDEFINED_VAR, List.of(spec(
                        "Customer",
                        "- id: UUID generated",
                        useCase(
                                "Delete Customer",
                                "DELETE /customers",
                                "",
                                "delete customer\nreturn nothing",
                                "204 nothing",
                                "")))),
                new SemanticCase("missing return", ErrorCodes.SEMANTIC_FLOW_RETURN, List.of(spec(
                        "Customer",
                        "- id: UUID generated",
                        useCase(
                                "List Customers",
                                "GET /customers",
                                "",
                                "customers = list Customer",
                                "200 List<Customer>",
                                "")))),
                new SemanticCase("return type", ErrorCodes.SEMANTIC_RETURN_TYPE, List.of(spec(
                        "Customer",
                        "- id: UUID generated",
                        useCase(
                                "Create Customer",
                                "POST /customers",
                                "",
                                "customer = create Customer from input\nreturn nothing",
                                "201 Customer",
                                "")))),
                new SemanticCase("foreign entity", ErrorCodes.SEMANTIC_FOREIGN_ENTITY, List.of(spec(
                        "Customer",
                        "- id: UUID generated",
                        useCase(
                                "Create Customer",
                                "POST /customers",
                                "",
                                "account = create Account from input\nreturn account",
                                "201 Account",
                                "")))),
                new SemanticCase("unknown input", ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN, List.of(spec(
                        "Customer",
                        "- id: UUID generated",
                        useCase(
                                "Create Customer",
                                "POST /customers",
                                "- name: String required",
                                "customer = create Customer from input\nreturn customer",
                                "201 Customer",
                                "")))),
                new SemanticCase("input type", ErrorCodes.SEMANTIC_INPUT_FIELD_TYPE, List.of(spec(
                        "Customer",
                        "- id: UUID generated\n- age: Long",
                        useCase(
                                "Create Customer",
                                "POST /customers",
                                "- age: Int",
                                "customer = create Customer from input\nreturn customer",
                                "201 Customer",
                                "")))),
                new SemanticCase("unreachable duplicate", ErrorCodes.SEMANTIC_DUPLICATE_ON_NON_UNIQUE, List.of(spec(
                        "Customer",
                        "- id: UUID generated\n- email: Email",
                        useCase(
                                "Create Customer",
                                "POST /customers",
                                "- email: Email",
                                "customer = create Customer from input\nsave customer\nreturn customer",
                                "201 Customer",
                                "- duplicate email -> 409")))),
                new SemanticCase("duplicate use case", ErrorCodes.SEMANTIC_DUPLICATE_USE_CASE, List.of(
                        spec("Customer", "- id: UUID generated", listUseCaseNamed(
                                "List Records", "Customer", "/customers")),
                        spec("Account", "- id: UUID generated", listUseCaseNamed(
                                "List Records", "Account", "/accounts")))),
                new SemanticCase("orphan", ErrorCodes.SEMANTIC_ORPHAN_ENTITY, List.of(spec(
                        "Customer", "- id: UUID generated", ""))));

        for (SemanticCase semanticCase : cases) {
            List<Diagnostic> diagnostics = validate(semanticCase.sources());
            assertThat(diagnostics)
                    .as(semanticCase.name())
                    .extracting(Diagnostic::code)
                    .contains(semanticCase.expectedCode());
        }
    }

    @Test
    void duplicateEntityReportsBothSourceLocations() {
        List<Diagnostic> diagnostics = validate(List.of(
                validCrud("Customer", "/customers"),
                validCrud("Customer", "/other-customers")));

        assertThat(diagnostics)
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_DUPLICATE_ENTITY))
                .hasSize(2)
                .extracting(diagnostic -> diagnostic.where().orElseThrow().file())
                .containsExactly("specs/spec-1.harpia.md", "specs/spec-2.harpia.md");
    }

    @Test
    void orphanEntityIsOnlyAWarning() {
        List<Diagnostic> diagnostics = validate(List.of(spec(
                "Customer", "- id: UUID generated", "")));

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SEMANTIC_ORPHAN_ENTITY);
            assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
        });
    }

    @Test
    void canonicalCrudIsSemanticallyValid() {
        assertThat(validate(List.of(validCrud("Customer", "/customers")))).isEmpty();
    }

    @Test
    void aReferenceToAnotherModulesEntityIsDistinguishedFromAnUndeclaredOne() {
        String customer = spec("Customer", "- id: UUID generated", listUseCase("Customer", "/customers"));
        String account = spec(
                "Account",
                "- id: UUID generated",
                listUseCaseNamed("List Records", "Customer", "/accounts"));

        List<Diagnostic> diagnostics = validate(List.of(customer, account));

        assertThat(diagnostics)
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FOREIGN_ENTITY))
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.message())
                            .as("V0 keeps a flow inside the module that declares its entity")
                            .contains("Harpia 0 keeps a flow inside the module")
                            .contains("'Customer' is declared elsewhere");
                    assertThat(diagnostic.related()).singleElement().satisfies(related ->
                            assertThat(related.where().file())
                                    .isEqualTo("specs/spec-1.harpia.md"));
                });
    }

    @Test
    void aReferenceToAnEntityNobodyDeclaresSaysSo() {
        String account = spec(
                "Account",
                "- id: UUID generated",
                listUseCaseNamed("List Records", "Ghost", "/accounts"));

        assertThat(validate(List.of(account)))
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FOREIGN_ENTITY))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("entity 'Ghost' is not declared"));
    }

    private static List<Diagnostic> validate(List<String> sources) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        List<ModuleAst> modules = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            SourceFile source = new SourceFile(
                    "specs/spec-" + (index + 1) + ".harpia.md", sources.get(index));
            modules.add(SpecParser.parse(source, LanguageVersion.V0, diagnostics).orElseThrow(() ->
                    new AssertionError("test fixture has syntax errors: " + diagnostics.diagnostics())));
        }
        ProjectAst project = new ProjectAst(LanguageVersion.V0, modules);
        SemanticValidator.validate(
                project,
                SymbolTable.declare(project, diagnostics),
                diagnostics);
        return diagnostics.diagnostics();
    }

    private static String validCrud(String entity, String path) {
        return spec(entity, "- id: UUID generated\n- name: String", listUseCase(entity, path));
    }

    private static String listUseCase(String entity, String path) {
        return listUseCaseNamed("List " + entity + "s", entity, path);
    }

    private static String listUseCaseNamed(String title, String entity, String path) {
        String variable = Character.toLowerCase(entity.charAt(0)) + entity.substring(1) + "s";
        return useCase(
                title,
                "GET " + path,
                "",
                variable + " = list " + entity + "\nreturn " + variable,
                "200 List<" + entity + ">",
                "");
    }

    private static String useCase(
            String title,
            String endpoint,
            String input,
            String flow,
            String output,
            String errors) {
        String inputSection = input.isBlank() ? "" : "\n### Input\n\n" + input + "\n";
        String errorsSection = errors.isBlank() ? "" : "\n### Errors\n\n" + errors + "\n";
        return """

                ## %s

                ### Endpoint

                %s

                ### Access

                public
                %s
                ### Flow

                ```flow
                %s
                ```

                ### Output

                %s
                %s
                """.formatted(title, endpoint, inputSection, flow, output, errorsSection);
    }

    private static String spec(String entity, String fields, String useCases) {
        return """
                # %s

                ## Data

                %s
                %s
                """.formatted(entity, fields, useCases);
    }

    private record SemanticCase(String name, String expectedCode, List<String> sources) {
    }
}
