package dev.harpia;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ValidateExampleTest {

    @Test
    void canonicalCustomerExamplePassesTheWholeValidationPipeline() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer")));

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree()).isPresent();
        assertThat(result.tree().orElseThrow().files().keySet()).containsExactly(
                "pom.xml",
                "src/main/java/com/example/customer/CustomerServiceApplication.java",
                "src/main/java/com/example/customer/domain/Customer.java",
                "src/main/java/com/example/customer/dto/CreateCustomerRequest.java",
                "src/main/java/com/example/customer/dto/CustomerResponse.java",
                "src/main/java/com/example/customer/dto/UpdateCustomerRequest.java",
                "src/main/java/com/example/customer/error/ApiError.java",
                "src/main/java/com/example/customer/error/ApiExceptionHandler.java",
                "src/main/java/com/example/customer/error/NotFoundException.java",
                "src/main/java/com/example/customer/repository/CustomerRepository.java",
                "src/main/java/com/example/customer/service/CustomerService.java",
                "src/main/java/com/example/customer/web/CustomerController.java",
                "src/main/resources/application.yaml",
                "src/main/resources/db/migration/V1__init.sql",
                "src/test/java/com/example/customer/service/CustomerServiceTest.java",
                "src/test/java/com/example/customer/web/CustomerControllerTest.java");
    }
}
