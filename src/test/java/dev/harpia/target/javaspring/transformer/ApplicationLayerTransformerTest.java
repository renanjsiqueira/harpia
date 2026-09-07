package dev.harpia.target.javaspring.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import java.nio.file.Path;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;

/**
 * The application layer: every Harpia flow command must reach the generated service, and every
 * declared failure must reach the generated handler.
 */
class ApplicationLayerTransformerTest {

    private static final String BASE = "src/main/java/com/example/customer/";
    private static final String SERVICE = BASE + "service/CustomerService.java";
    private static final String CONTROLLER = BASE + "web/CustomerController.java";
    private static final String HANDLER = BASE + "error/ApiExceptionHandler.java";

    @Test
    void createFromInputBuildsTheEntityAndPersistsIt() {
        assertThat(service())
                .contains("Customer customer = new Customer();")
                .contains("customer.setName(request.name());")
                .contains("customer.setEmail(request.email());")
                .contains("customer = repository.save(customer);")
                .contains("return toResponse(customer);");
    }

    @Test
    void loadByIdFailsWithTheDeclaredNotFound() {
        assertThat(service())
                .contains("Customer customer = repository.findById(id)")
                .contains(".orElseThrow(() -> new NotFoundException(\"Customer\", id));");
    }

    @Test
    void listIsOrderedByIdBecauseTheLanguageRequiresIt() {
        assertThat(service())
                .contains("List<Customer> customers = repository.findAll(Sort.by(\"id\"));")
                .contains("return customers.stream().map(CustomerService::toResponse).toList();");
    }

    @Test
    void deleteReturnsNothingAndSoTheMethodIsVoid() {
        assertThat(service())
                .contains("public void deleteCustomer(UUID id)")
                .contains("repository.delete(customer);");
    }

    @Test
    void theTransactionBoundaryFollowsTheOperationNotTheCode() {
        String service = service();

        assertThat(service)
                .contains("    @Transactional\n    public CustomerResponse createCustomer(")
                .contains("    @Transactional(readOnly = true)"
                        + "\n    public CustomerResponse getCustomer(")
                .contains("    @Transactional(readOnly = true)"
                        + "\n    public List<CustomerResponse> listCustomers(");
    }

    @Test
    void validateInputProducesNoServiceStatementAndMarksTheBodyInstead() {
        assertThat(service())
                .as("validation is enforced at the HTTP boundary, not inside the flow")
                .doesNotContain("validate");
        assertThat(controller())
                .contains("@Valid @RequestBody CreateCustomerRequest request");
    }

    @Test
    void aFlowWithoutValidateInputDoesNotValidateItsBody() {
        assertThat(controller())
                .contains("public ResponseEntity<List<CustomerResponse>> listCustomers()");
    }

    @Test
    void theControllerReturnsTheDeclaredStatus() {
        assertThat(controller())
                .contains("@PostMapping(\"/customers\")")
                .contains("return ResponseEntity.status(201).body(service.createCustomer(request));")
                .contains("@DeleteMapping(\"/customers/{id}\")")
                .contains("return ResponseEntity.status(204).build();")
                .contains("@PathVariable UUID id");
    }

    @Test
    void theControllerHoldsNoRule() {
        assertThat(controller())
                .doesNotContain("repository")
                .doesNotContain("Transactional")
                .doesNotContain("new Customer(");
    }

    @Test
    void everyDeclaredFailureIsMappedToItsDeclaredStatus() {
        assertThat(handler())
                .contains("@ExceptionHandler(NotFoundException.class)")
                .contains("ResponseEntity.status(404)")
                .contains("@ExceptionHandler(MethodArgumentNotValidException.class)")
                .contains("ResponseEntity.status(400)")
                .contains("@ExceptionHandler(DataIntegrityViolationException.class)")
                .contains("ResponseEntity.status(409)");
    }

    @Test
    void aDuplicateIsRecognisedByTheConstraintTheMigrationCreates() {
        String constraint = "uq_customer_email";

        assertThat(handler()).contains("if (cause.contains(\"" + constraint + "\")) {");
        assertThat(files().get("src/main/resources/db/migration/V1__init.sql"))
                .as("the handler and the schema must agree on the constraint name")
                .contains("CONSTRAINT " + constraint + " UNIQUE (email)");
    }

    @Test
    void requestModelsCarryValidationAndResponseModelsDoNot() {
        assertThat(files().get(BASE + "dto/CreateCustomerRequest.java"))
                .contains("public record CreateCustomerRequest(")
                .contains("@NotBlank String name,")
                .contains("@Email @NotBlank String email)");
        assertThat(files().get(BASE + "dto/CustomerResponse.java"))
                .contains("public record CustomerResponse(")
                .contains("UUID id,")
                .doesNotContain("@NotBlank");
    }

    @Test
    void aProjectThatDeclaresNoFailureGeneratesNoErrorHandling() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/business-logic/pricing")));

        assertThat(result.tree().orElseThrow().files().keySet())
                .noneMatch(path -> path.contains("/error/"));
    }

    private static String service() {
        return files().get(SERVICE);
    }

    private static String controller() {
        return files().get(CONTROLLER);
    }

    private static String handler() {
        return files().get(HANDLER);
    }

    private static SortedMap<String, String> files() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer")));
        assertThat(result.diagnostics()).isEmpty();
        return result.tree().orElseThrow().files();
    }
}
