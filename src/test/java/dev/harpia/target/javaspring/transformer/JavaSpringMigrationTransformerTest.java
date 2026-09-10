package dev.harpia.target.javaspring.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CustomerFixture;
import dev.harpia.target.javaspring.model.SqlMigrationModel;
import org.junit.jupiter.api.Test;

class JavaSpringMigrationTransformerTest {

    @Test
    void infersSchemaBeforeTheSqlTemplateRuns() {
        SqlMigrationModel migration = new JavaSpringMigrationTransformer()
                .transform(CustomerFixture.load().application());

        assertThat(migration.tables()).singleElement().satisfies(table -> {
            assertThat(table.name()).isEqualTo("customer");
            assertThat(table.columns()).containsExactly(
                    "id uuid NOT NULL",
                    "name varchar(255) NOT NULL",
                    "email varchar(320) NOT NULL",
                    "active boolean NOT NULL");
            assertThat(table.constraints()).containsExactly(
                    "CONSTRAINT pk_customer PRIMARY KEY (id)",
                    "CONSTRAINT uq_customer_email UNIQUE (email)");
        });
        assertThat(migration.foreignKeys()).isEmpty();
        assertThat(migration.source()).isPresent();
    }
}
