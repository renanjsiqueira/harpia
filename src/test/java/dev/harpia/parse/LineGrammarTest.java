package dev.harpia.parse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import dev.harpia.parse.SpecAst.CreateFrom;
import dev.harpia.parse.SpecAst.Delete;
import dev.harpia.parse.SpecAst.ListAll;
import dev.harpia.parse.SpecAst.LoadById;
import dev.harpia.parse.SpecAst.Return;
import dev.harpia.parse.SpecAst.Save;
import dev.harpia.parse.SpecAst.UpdateFrom;
import dev.harpia.parse.SpecAst.ValidateInput;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LineGrammarTest {

    private static final SourceRef WHERE = SourceRef.of("specs/test.harpia.md", 9, 3);

    @ParameterizedTest
    @MethodSource("validDataFields")
    void parsesValidDataFields(String line, String type) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        SpecAst.FieldDeclaration field = FieldLineParser.parseData(line, WHERE, diagnostics).orElseThrow();

        assertThat(field.type()).isEqualTo(type);
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    static Stream<Arguments> validDataFields() {
        return Stream.of(
                Arguments.of("- name: String required", "String"),
                Arguments.of("* notes: Text", "Text"),
                Arguments.of("+ age: Int default -1", "Int"),
                Arguments.of("- sequence: Long unique", "Long"),
                Arguments.of("- amount: Decimal default 10.50", "Decimal"),
                Arguments.of("- active: Boolean default true", "Boolean"),
                Arguments.of("- id: UUID generated", "UUID"),
                Arguments.of("- email: Email required unique", "Email"),
                Arguments.of("- birthday: Date", "Date"),
                Arguments.of("- createdAt: DateTime", "DateTime"));
    }

    @ParameterizedTest
    @MethodSource("validFlows")
    void parsesAllEightFlowCommands(String line, Class<?> statementType) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        Object statement = FlowLineParser.parse(line, WHERE, diagnostics).orElseThrow();

        assertThat(statement).isInstanceOf(statementType);
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    static Stream<Arguments> validFlows() {
        return Stream.of(
                Arguments.of("validate input", ValidateInput.class),
                Arguments.of("customer = create Customer from input", CreateFrom.class),
                Arguments.of("customer = load Customer by id", LoadById.class),
                Arguments.of("update customer from input", UpdateFrom.class),
                Arguments.of("customers = list Customer", ListAll.class),
                Arguments.of("save customer", Save.class),
                Arguments.of("delete customer", Delete.class),
                Arguments.of("return nothing", Return.class));
    }

    @Test
    void parsesEndpointAccessInputOutputAndErrors() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(EndpointParser.parse("GET /customers/{id}", WHERE, diagnostics)).isPresent();
        assertThat(AccessParser.parse("public", WHERE, diagnostics)).contains(SpecAst.Access.PUBLIC);
        assertThat(FieldLineParser.parseInput("- email: Email required", WHERE, diagnostics))
                .hasValueSatisfying(input -> assertThat(input.required()).isTrue());
        assertThat(OutputParser.parse("200 List<Customer>", WHERE, diagnostics))
                .hasValueSatisfying(output -> assertThat(output.shape().kind()).isEqualTo(SpecAst.OutputKind.LIST));
        assertThat(ErrorLineParser.parse("- duplicate email -> 409", WHERE, diagnostics))
                .hasValueSatisfying(error -> assertThat(error.field()).contains("email"));
        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidLines")
    void invalidLinesProduceTheirStableDiagnostic(
            String grammar, String line, String expectedCode) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        Optional<?> result = switch (grammar) {
            case "data" -> FieldLineParser.parseData(line, WHERE, diagnostics);
            case "input" -> FieldLineParser.parseInput(line, WHERE, diagnostics);
            case "endpoint" -> EndpointParser.parse(line, WHERE, diagnostics);
            case "access" -> AccessParser.parse(line, WHERE, diagnostics);
            case "flow" -> FlowLineParser.parse(line, WHERE, diagnostics);
            case "output" -> OutputParser.parse(line, WHERE, diagnostics);
            case "error" -> ErrorLineParser.parse(line, WHERE, diagnostics);
            default -> throw new AssertionError("unknown test grammar " + grammar);
        };

        assertThat(result).isEmpty();
        assertThat(diagnostics.diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(expectedCode);
                    assertThat(diagnostic.where()).hasValueSatisfying(where -> {
                        assertThat(where.line()).isEqualTo(9);
                        assertThat(where.column()).isPositive();
                    });
                });
    }

    static Stream<Arguments> invalidLines() {
        return Stream.of(
                Arguments.of("data", "- Bad_name String", ErrorCodes.SYNTAX_FIELD_LINE),
                // A PascalCase name may be a declared type, so the parser accepts its shape and
                // whether it resolves is decided against the whole project. A name that cannot be
                // any type is still a syntax error here.
                Arguments.of("data", "- amount: money", ErrorCodes.SYNTAX_UNKNOWN_TYPE),
                Arguments.of("data", "- name: String required required", ErrorCodes.SYNTAX_FIELD_LINE),
                Arguments.of("input", "- id: UUID generated", ErrorCodes.SYNTAX_FIELD_LINE),
                Arguments.of("endpoint", "PATCH /customers/{id}", ErrorCodes.SYNTAX_ENDPOINT),
                // A named parameter is valid syntax; whether it names an input is semantic.
                Arguments.of("endpoint", "GET /customers/{CustomerId}", ErrorCodes.SYNTAX_ENDPOINT),
                Arguments.of("endpoint", "GET /customers/{}", ErrorCodes.SYNTAX_ENDPOINT),
                Arguments.of("access", "authenticated", ErrorCodes.UNSUPPORTED_AUTHENTICATION),
                Arguments.of("flow", "find Customer by email", ErrorCodes.SYNTAX_FLOW_COMMAND),
                Arguments.of("output", "404 Customer", ErrorCodes.SYNTAX_USE_CASE_SECTION),
                // A bare phrase is now a domain error, so what stays invalid is a malformed one.
                Arguments.of("error", "- Forbidden -> 403", ErrorCodes.SYNTAX_ERROR_CONDITION),
                Arguments.of("error", "- duplicate -> 409", ErrorCodes.SYNTAX_ERROR_CONDITION),
                Arguments.of("error", "- forbidden -> 200", ErrorCodes.SYNTAX_ERROR_CONDITION));
    }

    @Test
    void unknownTypePointsAtTheTypeToken() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        FieldLineParser.parseData("- amount: money", WHERE, diagnostics);

        assertThat(diagnostics.diagnostics().getFirst().where())
                .hasValue(SourceRef.of(WHERE.file(), WHERE.line(), 13));
    }

    @Test
    void relationshipSyntaxGetsTheSpecificOutOfScopeDiagnostic() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        assertThat(FieldLineParser.parseData("- customer: UUID -> Customer", WHERE, diagnostics)).isEmpty();

        assertThat(diagnostics.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly(ErrorCodes.UNSUPPORTED_RELATIONSHIP);
        assertThat(diagnostics.diagnostics().getFirst().hint())
                .hasValueSatisfying(hint -> assertThat(hint).contains("Fora do escopo"));
    }
}
