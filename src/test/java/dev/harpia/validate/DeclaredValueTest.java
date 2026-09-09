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
 * A type compared by what it holds, rather than by an identity.
 *
 * <p>An entity is one row with an id. A value is a group of fields that travels inside one, so it
 * owns no table, no id and no column of its own: it is stored as the columns it groups, under the
 * name of the field that holds it.
 */
class DeclaredValueTest {

    private static final String VALUE = "src/main/java/com/example/order/domain/Address.java";
    private static final String ENTITY = "src/main/java/com/example/order/domain/Shipment.java";
    private static final String MIGRATION = "src/main/resources/db/migration/V1__init.sql";

    @TempDir
    Path projectRoot;

    @Test
    void aDeclaredValueIsEmbeddedRatherThanGivenATableOfItsOwn(@TempDir Path classes)
            throws IOException {
        project("""
                ## Value Address

                - street: String required
                - city: String required
                - zip: String required
                """, "Address");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Value Address(street: STRING, city: STRING, zip: STRING)");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(VALUE))
                .as("JPA embeds by field access, so it needs a class with a no-argument constructor")
                .contains("@Embeddable")
                .contains("public class Address {")
                .contains("protected Address() {")
                .contains("public Address(String street, String city, String zip)");
        assertThat(files.get(ENTITY))
                .contains("@Embedded")
                .as("the column names have to be the ones the migration created")
                .contains("@AttributeOverride(name = \"street\", "
                        + "column = @Column(name = \"destination_street\"))")
                .contains("private Address destination;");
        assertThat(files.get(MIGRATION))
                .as("a value owns no table; its fields become columns of the entity that holds it")
                .contains("destination_street varchar(255) NOT NULL")
                .contains("destination_zip varchar(255) NOT NULL")
                .doesNotContain("CREATE TABLE address");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aValueFieldCannotClaimIdentity() throws IOException {
        project("""
                ## Value Address

                - street: String required unique
                """, "Address");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_VALUE_FIELD))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot be generated or unique")
                        .contains("a value has no identity"));
    }

    @Test
    void aValueWithoutFieldsIsRefused() throws IOException {
        project("""
                ## Value Address
                """, "Address");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code().equals(ErrorCodes.SYNTAX_VALUE_FIELD))
                .singleElement()
                .satisfies(diagnostic ->
                        assertThat(diagnostic.message()).contains("declares no fields"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String declaration, String type) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/shipment.harpia.md"), """
                # Shipment

                DECLARATION

                ## Data

                - id: UUID generated
                - recipient: String required
                - destination: TYPE required

                ## Create Shipment

                ### Endpoint

                POST /shipments

                ### Access

                public

                ### Input

                - recipient: String required
                - destination: TYPE required

                ### Flow

                ```flow
                validate input
                shipment = create Shipment from input
                save shipment
                return shipment
                ```

                ### Output

                201 Shipment
                """
                .replace("DECLARATION", declaration)
                .replace("TYPE", type), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: order-service
                  group: com.example
                  artifact: order-service
                  package: com.example.order

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
