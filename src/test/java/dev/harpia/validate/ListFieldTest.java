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
 * {@code List<T>} as a field type rather than a shape only an output could have.
 *
 * <p>A collection has as many rows per owner as it has elements, so it cannot share the owner's
 * row. That single fact decides the whole mapping: its own table, keyed back to the owner, and no
 * column in the entity that declares it.
 */
class ListFieldTest {

    private static final String ENTITY = "src/main/java/com/example/catalog/domain/Product.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";
    private static final String REQUEST =
            "src/main/java/com/example/catalog/dto/CreateProductRequest.java";

    @TempDir
    Path projectRoot;

    @Test
    void aCollectionGetsItsOwnTableAndNoColumnInTheOwner(@TempDir Path classes) throws IOException {
        project(
                "- tags: List<String> required\n- channels: List<Channel>",
                1,
                "- name: String required\n- tags: List<String> required");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Column tags: List<STRING> required");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(ENTITY))
                .contains("@ElementCollection")
                .contains("@CollectionTable(name = \"product_tags\", "
                        + "joinColumns = @JoinColumn(name = \"product_id\"))")
                .contains("private List<String> tags;")
                .as("a collection of a declared enum still stores the name, not the ordinal")
                .contains("private List<Channel> channels;");

        String migration = files.get(MIGRATION);
        String ownerTable = migration.substring(
                migration.indexOf("CREATE TABLE product ("),
                migration.indexOf(");", migration.indexOf("CREATE TABLE product (")));
        assertThat(ownerTable)
                .as("the owner's row has no room for a collection")
                .doesNotContain("tags");
        assertThat(migration)
                .contains("CREATE TABLE product_tags (")
                .contains("    product_id uuid NOT NULL")
                .contains("    tags varchar(255) NOT NULL")
                .contains("CONSTRAINT fk_product_tags_product_id FOREIGN KEY (product_id)");
        assertThat(files.get(REQUEST))
                .as("required on a collection is about having something in it")
                .contains("@NotEmpty List<String> tags");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aCollectionOfEntitiesIsStillARelationship() throws IOException {
        project("- related: List<Product>", 1);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.UNSUPPORTED_RELATIONSHIP))
                .isNotEmpty();
    }

    @Test
    void aCollectionOfCollectionsIsRefused() throws IOException {
        project("- matrix: List<List<String>>", 1);

        assertThat(compile().diagnostics())
                .as("the element of a collection is a single type name")
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_UNKNOWN_TYPE))
                .isNotEmpty()
                .allSatisfy(diagnostic ->
                        assertThat(diagnostic.message()).contains("List<List<String>>"));
    }

    @Test
    void aCollectionOfValuesIsRefused() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/product.harpia.md"), """
                # Product

                ## Value Tag

                - label: String required

                ## Data

                - id: UUID generated
                - name: String required
                - tags: List<Tag>

                ## Create Product

                ### Endpoint

                POST /products

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                product = create Product from input
                save product
                return product
                ```

                ### Output

                201 Product
                """, StandardCharsets.UTF_8);
        config(1);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_UNKNOWN_TYPE))
                .isNotEmpty()
                .allSatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("a collection holds a scalar or a declared enum"));
    }

    @Test
    void collectionsAreNotPartOfV0() throws IOException {
        project("- tags: List<String>", 0);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_UNKNOWN_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("collections need harpia.languageVersion 1"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String extraFields, int languageVersion) throws IOException {
        project(extraFields, languageVersion, "- name: String required");
    }

    private void project(String extraFields, int languageVersion, String input)
            throws IOException {
        String enumeration = languageVersion == 1 ? """
                ## Enum Channel

                - online
                - retail

                """ : "";
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/product.harpia.md"), """
                # Product

                """ + enumeration + """
                ## Data

                - id: UUID generated
                - name: String required
                """ + extraFields + """

                ## Create Product

                ### Endpoint

                POST /products

                ### Access

                public

                ### Input

                """ + input + """

                ### Flow

                ```flow
                validate input
                product = create Product from input
                save product
                return product
                ```

                ### Output

                201 Product
                """, StandardCharsets.UTF_8);
        config(languageVersion);
    }

    private void config(int languageVersion) throws IOException {
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: VERSION

                project:
                  name: catalog-service
                  group: com.example
                  artifact: catalog-service
                  package: com.example.catalog

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
