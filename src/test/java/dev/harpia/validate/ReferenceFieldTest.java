package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.GeneratedJava;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A field that points at another entity's identity.
 *
 * <p>It is a type, not a relationship. It says which entity is meant and which row, and says
 * nothing about loading it, cascading to it or owning its lifetime — so the schema states
 * referential integrity and the entity holds an id, with no association mapping anywhere.
 */
class ReferenceFieldTest {

    private static final String ENTITY = "src/main/java/com/example/orders/domain/Purchase.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    @Test
    void aReferenceIsAForeignKeyAndNotAnAssociation(@TempDir Path classes) throws IOException {
        project("- buyer: Reference<Customer> required", 1);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Column buyer_id: Reference<Customer> required");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(ENTITY))
                .as("the field holds the identity of the row, not the row")
                .contains("@Column(name = \"buyer_id\", nullable = false)")
                .contains("private UUID buyer;")
                .as("an association would import a fetch strategy and a cascade nothing declared")
                .doesNotContain("ManyToOne")
                .doesNotContain("JoinColumn");

        String purchase = files.get(MIGRATION);
        assertThat(purchase)
                .as("referential integrity is the one thing a reference does promise")
                .contains("buyer_id uuid NOT NULL")
                .contains("CONSTRAINT fk_purchase_buyer_id FOREIGN KEY (buyer_id) "
                        + "REFERENCES customer (id)");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aReferenceMustNameSomethingWithAnIdentity() throws IOException {
        project("- buyer: Reference<Channel>", 1);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_UNKNOWN_TYPE))
                .isNotEmpty()
                .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("does not name a declared entity")
                        .contains("points at something with an identity"));
    }

    @Test
    void referencesAreNotPartOfV0() throws IOException {
        project("- buyer: Reference<Customer>", 0);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_UNKNOWN_TYPE))
                .isNotEmpty()
                .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("Reference needs harpia.languageVersion 1"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String reference, int languageVersion) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## Create Customer

                ### Endpoint

                POST /customers

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                customer = create Customer from input
                save customer
                return customer
                ```

                ### Output

                201 Customer
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("specs/purchase.harpia.md"), """
                # Purchase

                ## Data

                - id: UUID generated
                - total: Decimal required
                """ + reference + """

                ## Create Purchase

                ### Endpoint

                POST /purchases

                ### Access

                public

                ### Input

                - total: Decimal required

                ### Flow

                ```flow
                validate input
                purchase = create Purchase from input
                save purchase
                return purchase
                ```

                ### Output

                201 Purchase
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: VERSION

                project:
                  name: orders-service
                  group: com.example
                  artifact: orders-service
                  package: com.example.orders

                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.3.6"

                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """.replace("VERSION", String.valueOf(languageVersion)), StandardCharsets.UTF_8);
    }
}
