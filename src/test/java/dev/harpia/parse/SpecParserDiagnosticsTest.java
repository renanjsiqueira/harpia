package dev.harpia.parse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LanguageVersion;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.source.SourceFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpecParserDiagnosticsTest {

    @ParameterizedTest
    @MethodSource("invalidStructures")
    void reportsStableStructuralDiagnostic(String source, String expectedCode) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(SpecParser.parse(file(source), LanguageVersion.V0, diagnostics)).isEmpty();

        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .contains(expectedCode);
    }

    static Stream<Arguments> invalidStructures() {
        return Stream.of(
                Arguments.of("""
                        ## Data
                        - id: UUID generated
                        """, ErrorCodes.SYNTAX_H1),
                Arguments.of("""
                        # Customer
                        # Account
                        ## Data
                        - id: UUID generated
                        """, ErrorCodes.SYNTAX_H1),
                Arguments.of("""
                        # customer_name
                        ## Data
                        - id: UUID generated
                        """, ErrorCodes.SYNTAX_ENTITY_NAME),
                Arguments.of("""
                        # Customer
                        """, ErrorCodes.SYNTAX_DATA_SECTION),
                Arguments.of("""
                        # Customer
                        ## Data
                        - id: UUID generated
                        ## Data
                        - name: String
                        """, ErrorCodes.SYNTAX_DATA_SECTION),
                Arguments.of("""
                        # Customer
                        ## Data
                        - id: UUID generated
                        ## Create Customer
                        ### Endpoint
                        POST /customers
                        """, ErrorCodes.SYNTAX_USE_CASE_SECTION),
                Arguments.of("""
                        # Customer
                        ## Data
                        - id: UUID generated
                        ## create customer
                        """, ErrorCodes.SYNTAX_USE_CASE_TITLE),
                Arguments.of("""
                        # Customer
                        ## Data
                        - id: UUID generated
                        ## Email
                        """, ErrorCodes.UNSUPPORTED_EMAIL_OR_EVENTS),
                Arguments.of("""
                        # Customer
                        ## Data
                        - owner: UUID -> Owner
                        """, ErrorCodes.UNSUPPORTED_RELATIONSHIP),
                Arguments.of("""
                        # Customer
                        ## Data
                        - id: UUID generated
                        ## Get Customer
                        ### Endpoint
                        GET /customers/{id}
                        ### Access
                        admin
                        ### Flow
                        ```flow
                        customer = load Customer by id
                        return customer
                        ```
                        ### Output
                        200 Customer
                        """, ErrorCodes.UNSUPPORTED_AUTHENTICATION));
    }

    @Test
    void reportsIndependentErrorsInOnePass() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        SourceFile source = file("""
                # customer_name

                ## Data

                - amount: money
                - owner: UUID -> Owner

                ## create customer

                ### Endpoint

                TRACE /customers

                ### Access

                admin

                ### Mystery

                anything
                """);

        assertThat(SpecParser.parse(source, LanguageVersion.V0, diagnostics)).isEmpty();

        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .contains(
                        ErrorCodes.SYNTAX_ENTITY_NAME,
                        ErrorCodes.SYNTAX_UNKNOWN_TYPE,
                        ErrorCodes.UNSUPPORTED_RELATIONSHIP,
                        ErrorCodes.SYNTAX_USE_CASE_TITLE,
                        ErrorCodes.SYNTAX_ENDPOINT,
                        ErrorCodes.UNSUPPORTED_AUTHENTICATION,
                        ErrorCodes.SYNTAX_USE_CASE_SECTION);
        assertThat(diagnostics.diagnostics().size()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void rejectsDuplicateAndMalformedExecutableSubsections() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        SourceFile source = file("""
                # Customer

                ## Data

                - id: UUID generated

                ## List Customers

                ### Endpoint

                GET /customers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Flow

                list Customer

                ### Output

                200 List<Customer>
                """);

        assertThat(SpecParser.parse(source, LanguageVersion.V0, diagnostics)).isEmpty();

        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsOnly(ErrorCodes.SYNTAX_USE_CASE_SECTION);
        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .anyMatch(message -> message.contains("repeats '### Endpoint'"))
                .anyMatch(message -> message.contains("fenced code block"));
    }

    private static SourceFile file(String source) {
        return new SourceFile("specs/invalid.harpia.md", source);
    }
}
