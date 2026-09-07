package dev.harpia.target.javaspring.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import java.nio.file.Path;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;

/**
 * The generated tests must observe what the specification declares, not merely exist. Each case
 * below names the Harpia construct it comes from.
 */
class GeneratedTestTransformerTest {

    private static final String SERVICE_TEST =
            "src/test/java/com/example/customer/service/CustomerServiceTest.java";
    private static final String CONTROLLER_TEST =
            "src/test/java/com/example/customer/web/CustomerControllerTest.java";

    @Test
    void everyFlowGetsATestNamedAfterWhatItObserves() {
        assertThat(serviceTest())
                .contains("void createCustomerPersistsWhatTheRequestCarries()")
                .contains("void getCustomerReturnsTheStoredRecord()")
                .contains("void listCustomersReturnsEveryRecordInDeclaredOrder()")
                .contains("void updateCustomerCopiesTheRequestOntoTheStoredRecord()")
                .contains("void deleteCustomerRemovesTheStoredRecord()");
    }

    @Test
    void aFlowThatLoadsByIdIsAlsoTestedForTheMissingCase() {
        assertThat(serviceTest())
                .contains("void getCustomerFailsWhenNothingIsStored()")
                .contains("void updateCustomerFailsWhenNothingIsStored()")
                .contains("void deleteCustomerFailsWhenNothingIsStored()")
                .contains(".isInstanceOf(NotFoundException.class);");
    }

    @Test
    void createAssertsThatTheRequestReachedThePersistedEntity() {
        assertThat(serviceTest())
                .contains("Mockito.verify(repository).save(saved.capture());")
                .contains("assertThat(saved.getValue().getEmail()).isEqualTo(request.email());");
    }

    @Test
    void listAssertsTheStableOrderTheLanguageRequires() {
        assertThat(serviceTest())
                .contains("ArgumentCaptor<Sort> order = ArgumentCaptor.forClass(Sort.class);")
                .contains("isEqualTo(Sort.by(\"id\"));");
    }

    @Test
    void deleteAssertsTheRecordIsActuallyRemoved() {
        assertThat(serviceTest()).contains("Mockito.verify(repository).delete(entity);");
    }

    @Test
    void everyEndpointIsTestedForItsDeclaredStatus() {
        assertThat(controllerTest())
                .contains("MockMvcRequestBuilders.post(\"/customers\")")
                .contains("MockMvcResultMatchers.status().is(201)")
                .contains("MockMvcRequestBuilders.delete(\"/customers/{id}\", ID))")
                .contains("MockMvcResultMatchers.status().is(204)");
    }

    @Test
    void everyDeclaredFailureIsTestedForItsDeclaredStatus() {
        assertThat(controllerTest())
                .contains("void createCustomerRejectsAnIncompleteBody()")
                .contains("MockMvcResultMatchers.status().is(400)")
                .contains("void getCustomerReportsAMissingRecord()")
                .contains("MockMvcResultMatchers.status().is(404)")
                .contains("void createCustomerReportsADuplicate()")
                .contains("MockMvcResultMatchers.status().is(409)");
    }

    @Test
    void theDuplicateTestUsesTheConstraintTheMigrationCreates() {
        assertThat(controllerTest())
                .contains("unique constraint \\\"uq_customer_email\\\"")
                .contains("jsonPath(\"$.message\").value(\"email already exists\")");
        assertThat(files().get("src/main/resources/db/migration/V1__init.sql"))
                .contains("CONSTRAINT uq_customer_email UNIQUE (email)");
    }

    @Test
    void sampleValuesAreDerivedFromTheSpecificationAndNeverRandom() {
        assertThat(serviceTest())
                .contains("UUID.fromString(\"00000000-0000-0000-0000-000000000001\")")
                .contains("new CreateCustomerRequest(\"name\", \"email@example.com\")")
                .doesNotContain("random")
                .doesNotContain("Instant.now");
    }

    @Test
    void aProjectWithoutOperationsStillGetsTestsForItsComputations() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/business-logic/pricing")));

        assertThat(result.tree().orElseThrow().files().keySet())
                .as("a scenario is what makes a pure computation testable")
                .contains(
                        "src/test/java/com/example/pricing/logic/CalculateDiscountTest.java",
                        "src/test/java/com/example/pricing/logic/CalculateTotalTest.java");
    }

    private static String serviceTest() {
        return files().get(SERVICE_TEST);
    }

    private static String controllerTest() {
        return files().get(CONTROLLER_TEST);
    }

    private static SortedMap<String, String> files() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer")));
        assertThat(result.diagnostics()).isEmpty();
        return result.tree().orElseThrow().files();
    }
}
