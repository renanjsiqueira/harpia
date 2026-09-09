package dev.harpia.binding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LanguageVersion;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.source.SourceFile;
import org.junit.jupiter.api.Test;

class BindingParserTest {

    @Test
    void parsesAFormalHttpBindingFile() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        BindingAst file = BindingParser.parse(
                source(binding("GetCustomer", "GET /customers/{id}")),
                LanguageVersion.V1,
                diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(file.file()).isEqualTo("bindings/customer.harpia.md");
        assertThat(file.baseUrl()).isEmpty();
        assertThat(file.httpBindings()).singleElement().satisfies(binding -> {
            assertThat(binding.operation()).isEqualTo("GetCustomer");
            assertThat(binding.endpoint().method()).isEqualTo("GET");
            assertThat(binding.endpoint().path()).isEqualTo("/customers/{id}");
            assertThat(binding.access()).isEqualTo(BindingAst.Access.PUBLIC);
            assertThat(binding.request()).singleElement().isInstanceOfSatisfying(
                    BindingAst.Path.class,
                    mapping -> {
                        assertThat(mapping.input()).isEqualTo("id");
                        assertThat(mapping.parameter()).isEqualTo("id");
                    });
            assertThat(binding.response()).isInstanceOf(BindingAst.ResponseBody.class);
        });
    }

    @Test
    void parsesBaseUrlAndEveryRequestMappingKind() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        BindingAst file = BindingParser.parse(source("""
                # HTTP Bindings

                ## Base URL

                /api/v1

                ## Bind UpdateCustomer

                ### Endpoint

                PUT /customers/{id}

                ### Access

                public

                ### Request

                - id: path id
                - name: query customer-name
                - email: header X-Customer-Email

                ### Response

                output: body
                """), LanguageVersion.V1, diagnostics).orElseThrow();

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(file.baseUrl()).get().extracting(BindingAst.BaseUrl::path)
                .isEqualTo("/api/v1");
        assertThat(file.httpBindings().getFirst().request())
                .hasExactlyElementsOfTypes(
                        BindingAst.Path.class,
                        BindingAst.Query.class,
                        BindingAst.Header.class);
    }

    @Test
    void externalBindingsAreADeclaredV1Feature() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(BindingParser.parse(
                source(binding("GetCustomer", "GET /customers/{id}")),
                LanguageVersion.V0,
                diagnostics)).isEmpty();

        assertThat(diagnostics.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.SYNTAX_DECLARATION_TOO_NEW);
            assertThat(diagnostic.message()).contains("harpia.languageVersion 1");
        });
    }

    @Test
    void everyExternalHttpContractSectionIsRequired() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        BindingParser.parse(source("""
                # HTTP Bindings

                ## Bind GetCustomer

                ### Endpoint

                GET /customers/{id}

                ### Request

                - id: path id
                """), LanguageVersion.V1, diagnostics);

        assertThat(diagnostics.diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SYNTAX_BINDING_SECTION))
                .extracting(Diagnostic::message)
                .contains(
                        "binding for 'GetCustomer' is missing '### Access'",
                        "binding for 'GetCustomer' is missing '### Response'");
    }

    private static SourceFile source(String content) {
        return new SourceFile("bindings/customer.harpia.md", content);
    }

    private static String binding(String operation, String endpoint) {
        return """
                # HTTP Bindings

                ## Bind %s

                ### Endpoint

                %s

                ### Access

                public

                ### Request

                - id: path id

                ### Response

                output: body
                """.formatted(operation, endpoint);
    }
}
