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

/** {@code owned} is dependent lifecycle metadata, not target annotation syntax. */
class OwnedRelationshipTest {

    private static final String PURCHASE =
            "src/main/java/com/example/orders/domain/Purchase.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    @Test
    void ownedToOneAndToManyRelationshipsHaveDependentLifecycle(@TempDir Path classes)
            throws IOException {
        project("Customer owned", "List<OrderItem> owned");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("Field buyer: Customer required owned")
                .contains("Field items: List<OrderItem> required owned");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Field buyer: Customer required owned")
                .contains("Field items: List<OrderItem> required owned");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Relationship buyer: Customer cardinality=ONE loading=LAZY "
                        + "lifecycle=DEPENDENT required")
                .contains("Relationship items: List<OrderItem> cardinality=MANY loading=LAZY "
                        + "lifecycle=DEPENDENT required");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(PURCHASE))
                .contains("@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, "
                        + "orphanRemoval = true, optional = false)")
                .contains("@JoinColumn(name = \"buyer_id\", nullable = false, unique = true")
                .contains("@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, "
                        + "orphanRemoval = true)")
                .contains("inverseJoinColumns = @JoinColumn(name = \"order_item_id\", "
                        + "unique = true")
                .doesNotContain("@ManyToOne")
                .doesNotContain("@ManyToMany");

        assertThat(files.get(MIGRATION))
                .contains("CONSTRAINT uq_purchase_buyer_id UNIQUE (buyer_id)")
                .contains("CONSTRAINT uq_purchase_items_order_item_id UNIQUE (order_item_id)");
        GeneratedJava.compiles(files, classes);
    }

    @Test
    void ownedRejectsScalarsAndIdentityOnlyReferences() throws IOException {
        project("Reference<Customer> owned", "List<String> owned");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(
                        ErrorCodes.SEMANTIC_OWNED_RELATIONSHIP))
                .hasSize(2)
                .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("requires an Entity or List<Entity> relationship"));
    }

    @Test
    void duplicateOwnedModifierIsRejectedByTheFieldGrammar() throws IOException {
        project("Customer owned owned", "List<OrderItem>");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_FIELD_LINE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("duplicate field modifier 'owned'"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String buyerDeclaration, String itemsDeclaration) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        entity("customer", "Customer", "name");
        entity("order-item", "OrderItem", "description");
        Files.writeString(projectRoot.resolve("specs/purchase.harpia.md"), """
                # Purchase

                ## Data

                - id: UUID generated
                - buyer: BUYER required
                - items: ITEMS required

                ## Query Get Purchase

                ### Flow

                ```flow
                purchase = load Purchase by id
                return purchase
                ```

                ### Output

                200 Purchase
                """
                .replace("BUYER", buyerDeclaration)
                .replace("ITEMS", itemsDeclaration), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

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
                """, StandardCharsets.UTF_8);
    }

    private void entity(String file, String name, String descriptiveField) throws IOException {
        Files.writeString(projectRoot.resolve("specs/" + file + ".harpia.md"), """
                # NAME

                ## Data

                - id: UUID generated
                - FIELD: String required

                ## Query Get NAME

                ### Flow

                ```flow
                value = load NAME by id
                return value
                ```

                ### Output

                200 NAME
                """
                .replace("NAME", name)
                .replace("FIELD", descriptiveField), StandardCharsets.UTF_8);
    }
}
