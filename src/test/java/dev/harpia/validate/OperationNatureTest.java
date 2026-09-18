package dev.harpia.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.diag.Diagnostic;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Declaring an operation is only worth it if the compiler holds it to what was declared. These
 * tests are about the difference between saying what an operation is and guessing it from its flow.
 */
class OperationNatureTest {

    @TempDir
    Path projectRoot;

    @Test
    void aDeclaredCommandIsTransactionalWhateverItsStepsAre() throws IOException {
        project("""
                ## Command Touch Customer

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Flow

                ```flow
                customers = list Customer
                return customers
                ```

                ### Output

                200 List<Customer>
                """);

        assertThat(applicationIr())
                .as("a Command writes, even when this one happens not to")
                .contains("Operation touchCustomer nature=COMMAND kind=LIST transactional=true");
    }

    @Test
    void aDeclaredQueryIsNeverTransactional() throws IOException {
        project("""
                ## Query List Customers

                ### Endpoint

                GET /customers

                ### Access

                public

                ### Flow

                ```flow
                customers = list Customer
                return customers
                ```

                ### Output

                200 List<Customer>
                """);

        assertThat(applicationIr())
                .contains("Operation listCustomers nature=QUERY kind=LIST transactional=false");
    }

    @Test
    void aQueryThatMutatesIsRefused() throws IOException {
        project("""
                ## Query Wipe Customer

                ### Endpoint

                DELETE /customers/{id}

                ### Access

                public

                ### Flow

                ```flow
                customer = load Customer by id
                delete customer
                return nothing
                ```

                ### Output

                204 nothing
                """);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_QUERY_MUTATES))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot 'delete'")
                        .contains("belongs to a Command"));
    }

    /**
     * C13 — a Query is refused for the writes it reaches, not only for the ones it writes.
     *
     * <p>A Query that deletes is caught by the instruction it wrote. A Query that calls a Command
     * writes nothing itself and still mutates, and if the Command it calls only forwards to
     * another one, the write is two hops away from the promise being broken. Both shapes are
     * asserted here, because a rule that only looked at the first hop would pass the second.
     *
     * <p>The {@code emit} half of this check waits for S5: there is no such instruction yet, and
     * a test that pretended otherwise would be asserting a refusal the compiler cannot make.
     */
    @Test
    void queriesRejectTransitiveCommandEffectsAndEmit() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        project("""
                ## Query Read Customer

                ### Flow

                ```flow
                customer = load Customer by id
                call RecordVisit(name = name)
                return customer
                ```

                ### Output

                200 Customer
                """);
        Files.writeString(projectRoot.resolve("specs/visit.harpia.md"), """
                # Visit

                ## Data

                - id: UUID generated
                - name: String required

                ## Command RecordVisit

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                visit = create Visit from input
                call ArchiveVisit(name = name)
                save visit
                return nothing
                ```

                ### Output

                204 nothing

                ## Command ArchiveVisit

                ### Input

                - name: String required

                ### Flow

                ```flow
                validate input
                archived = create Visit from input
                save archived
                return nothing
                ```

                ### Output

                204 nothing
                """, StandardCharsets.UTF_8);

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_QUERY_MUTATES))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .as("the message walks the path, so the second hop is visible too")
                        .contains("Query 'ReadCustomer' reaches Command")
                        .contains("RecordVisit -> ArchiveVisit"));
    }

    @Test
    void anUndeclaredOperationStillHasItsNatureInferred() throws IOException {
        project("""
                ## Delete Customer

                ### Endpoint

                DELETE /customers/{id}

                ### Access

                public

                ### Flow

                ```flow
                customer = load Customer by id
                delete customer
                return nothing
                ```

                ### Output

                204 nothing
                """);

        assertThat(applicationIr())
                .as("V0 headings keep working; nothing already written changes meaning")
                .contains("Operation deleteCustomer nature=INFERRED kind=DELETE transactional=true");
    }

    private String applicationIr() {
        return Inspector.render(compile(), Stage.APPLICATION_IR).orElseThrow();
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(
                new CompileRequest(projectRoot, CompileRequest.Mode.VALIDATE));
    }

    private void project(String operation) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(
                projectRoot.resolve("specs/customer.harpia.md"),
                """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                """ + operation,
                StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve("harpia.yaml"),
                """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: customer-service
                  group: com.example
                  artifact: customer-service
                  package: com.example.customer

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
                """,
                StandardCharsets.UTF_8);
    }
}
