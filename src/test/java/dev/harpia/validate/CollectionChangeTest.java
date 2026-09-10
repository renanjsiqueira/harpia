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
 * Changing a collection in place, rather than replacing it.
 *
 * <p>{@code set} assigns a whole value. Saying "one more" is not the same as saying "these", so a
 * collection has its own way to change and a scalar cannot use it.
 */
class CollectionChangeTest {

    private static final String SERVICE =
            "src/main/java/com/example/catalog/service/ProductService.java";
    private static final String ENTITY =
            "src/main/java/com/example/catalog/domain/Product.java";

    @TempDir
    Path projectRoot;

    @Test
    void addAndRemoveChangeTheCollectionInPlace(@TempDir Path classes) throws IOException {
        project("add name to product.tags");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction ADD_TO name to tags");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .contains("product.getTags().add(request.name());");
        assertThat(files.get(ENTITY))
                .as("adding to a collection nobody assigned would fail on the first element")
                .contains("private List<String> tags = new ArrayList<>();");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void removeTakesAnElementOut(@TempDir Path classes) throws IOException {
        project("remove name from product.tags");

        SortedMap<String, String> files = compile().tree().orElseThrow().files();
        assertThat(files.get(SERVICE))
                .contains("product.getTags().remove(request.name());");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void anElementMustBeTheCollectionsElementType() throws IOException {
        project("add 1 to product.tags");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SEMANTIC_LOGIC_TYPE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .as("what joins a collection is an element, not the collection")
                        .contains("must be String but is Int"));
    }

    @Test
    void aScalarCannotBeAddedTo() throws IOException {
        project("add name to product.name");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .isNotEmpty()
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("is String, not a collection; use 'set' to assign it"));
    }

    @Test
    void aCollectionCannotBeAssignedWithSet() throws IOException {
        project("set product.tags = name");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .isNotEmpty()
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("use 'add' or 'remove' to change a collection"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String changeLine) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/product.harpia.md"), """
                # Product

                ## Data

                - id: UUID generated
                - name: String required
                - tags: List<String>

                ## Update Product

                ### Endpoint

                PUT /products/{id}

                ### Access

                public

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                product = load Product by id
                """ + changeLine + """

                save product
                return product
                ```

                ### Output

                200 Product

                ### Errors

                - not found -> 404
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

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
                """, StandardCharsets.UTF_8);
    }
}
