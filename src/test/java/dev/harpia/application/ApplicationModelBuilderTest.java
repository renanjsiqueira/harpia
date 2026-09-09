package dev.harpia.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CustomerFixture;
import org.junit.jupiter.api.Test;

/**
 * The Application IR describes application behaviour, not an implementation. It knows about
 * persistent entities, operations, request and response models, and transaction boundaries. It does
 * not know about repositories, services, controllers or Java types.
 */
class ApplicationModelBuilderTest {

    @Test
    void lowersEntitiesAndOperationsWithoutNamingAnyImplementationComponent() {
        ApplicationProject application = build();

        assertThat(application.entities()).singleElement().satisfies(entity -> {
            assertThat(entity.typeName()).isEqualTo("Customer");
            assertThat(entity.tableName()).isEqualTo("customer");
            assertThat(entity.responseTypeName()).isEqualTo("CustomerResponse");
            assertThat(entity.fields())
                    .extracting(ApplicationField::columnName)
                    .containsExactly("id", "name", "email", "active");
            assertThat(entity.fields())
                    .extracting(ApplicationField::scalarType)
                    .containsExactly(
                            ApplicationScalarType.UUID,
                            ApplicationScalarType.STRING,
                            ApplicationScalarType.EMAIL,
                            ApplicationScalarType.BOOLEAN);
            assertThat(entity.operations())
                    .extracting(ApplicationOperation::kind)
                    .containsExactly(
                            ApplicationOperation.Kind.CREATE,
                            ApplicationOperation.Kind.READ,
                            ApplicationOperation.Kind.LIST,
                            ApplicationOperation.Kind.UPDATE,
                            ApplicationOperation.Kind.DELETE);
            assertThat(entity.operations())
                    .extracting(ApplicationOperation::methodName)
                    .containsExactly(
                            "createCustomer",
                            "getCustomer",
                            "listCustomers",
                            "updateCustomer",
                            "deleteCustomer");
            assertThat(entity.operations())
                    .filteredOn(ApplicationOperation::transactional)
                    .extracting(ApplicationOperation::kind)
                    .containsExactly(
                            ApplicationOperation.Kind.CREATE,
                            ApplicationOperation.Kind.UPDATE,
                            ApplicationOperation.Kind.DELETE);
        });
    }

    @Test
    void settingsCarryOnlyTargetIndependentProjectIdentity() {
        ProjectSettings settings = build().settings();

        assertThat(settings.name()).isEqualTo("customer-service");
        assertThat(settings.namespace()).isEqualTo("com.example.customer");
        assertThat(settings.generation().migrations()).isTrue();
        assertThat(settings.generation().tests()).isTrue();
    }

    private static ApplicationProject build() {
        return CustomerFixture.load().application();
    }
}
