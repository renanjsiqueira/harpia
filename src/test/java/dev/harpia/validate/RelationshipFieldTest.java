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

/** Entity names are associations; {@code Reference<T>} remains an identity-only pointer. */
class RelationshipFieldTest {

    private static final String PURCHASE =
            "src/main/java/com/example/orders/domain/Purchase.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    @Test
    void toOneAndSharedToManyRelationshipsAreExplicitAndPersistent(@TempDir Path classes)
            throws IOException {
        project(1, "Customer", "List<OrderItem>");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Field buyer: Customer required")
                .contains("Field items: List<OrderItem> required");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Relationship buyer: Customer cardinality=ONE loading=LAZY "
                        + "lifecycle=INDEPENDENT required")
                .contains("Relationship items: List<OrderItem> cardinality=MANY loading=LAZY "
                        + "lifecycle=INDEPENDENT required");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(PURCHASE))
                .contains("@ManyToOne(fetch = FetchType.LAZY, optional = false)")
                .contains("@JoinColumn(name = \"buyer_id\", nullable = false, "
                        + "foreignKey = @ForeignKey(name = \"fk_purchase_buyer_id\"))")
                .contains("private Customer buyer;")
                .contains("@ManyToMany(fetch = FetchType.LAZY)")
                .contains("@JoinTable(name = \"purchase_items\"")
                .contains("inverseJoinColumns = @JoinColumn(name = \"order_item_id\"")
                .contains("private List<OrderItem> items = new ArrayList<>();")
                .as("an independent relationship never inherits persistence or removal")
                .doesNotContain("CascadeType")
                .doesNotContain("orphanRemoval");

        String migration = files.get(MIGRATION);
        assertThat(migration)
                .contains("buyer_id uuid NOT NULL")
                .contains("CONSTRAINT fk_purchase_buyer_id FOREIGN KEY (buyer_id) "
                        + "REFERENCES customer (id)")
                .contains("CREATE TABLE purchase_items (")
                .contains("purchase_id uuid NOT NULL")
                .contains("order_item_id uuid NOT NULL")
                .contains("CONSTRAINT pk_purchase_items PRIMARY KEY "
                        + "(purchase_id, order_item_id)")
                .contains("CONSTRAINT fk_purchase_items_purchase_id FOREIGN KEY (purchase_id) "
                        + "REFERENCES purchase (id)")
                .contains("CONSTRAINT fk_purchase_items_order_item_id "
                        + "FOREIGN KEY (order_item_id) REFERENCES order_item (id)");
        assertThat(migration.lastIndexOf("CREATE TABLE"))
                .as("cycles and reverse declaration order are safe because every FK is deferred")
                .isLessThan(migration.indexOf("ALTER TABLE"));

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void relationshipsNeedLanguageV1() throws IOException {
        project(0, "Customer", "String");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(
                        ErrorCodes.UNSUPPORTED_RELATIONSHIP))
                .singleElement();
    }

    @Test
    void aBareUnknownTypeIsNotMistakenForARelationship() throws IOException {
        project(1, "MissingCustomer", "String");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_UNKNOWN_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("unknown type 'MissingCustomer'"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(int languageVersion, String buyerType, String itemsType)
            throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        entity("customer", "Customer", "name");
        entity("order-item", "OrderItem", "description");
        Files.writeString(projectRoot.resolve("specs/purchase.harpia.md"), """
                # Purchase

                ## Data

                - id: UUID generated
                - total: Decimal required
                - buyer: BUYER required
                - items: ITEMS required

                ## Get Purchase

                ### Endpoint

                GET /purchases/{id}

                ### Access

                public

                ### Flow

                ```flow
                purchase = load Purchase by id
                return purchase
                ```

                ### Output

                200 Purchase
                """
                .replace("BUYER", buyerType)
                .replace("ITEMS", itemsType), StandardCharsets.UTF_8);
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

    private void entity(String file, String name, String descriptiveField) throws IOException {
        Files.writeString(projectRoot.resolve("specs/" + file + ".harpia.md"), """
                # NAME

                ## Data

                - id: UUID generated
                - FIELD: String required

                ## Get NAME

                ### Endpoint

                GET /FILE/{id}

                ### Access

                public

                ### Flow

                ```flow
                value = load NAME by id
                return value
                ```

                ### Output

                200 NAME
                """
                .replace("NAME", name)
                .replace("FILE", file)
                .replace("FIELD", descriptiveField), StandardCharsets.UTF_8);
    }
}
