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
 * Looking a record up by something other than its id.
 *
 * <p>{@code load ... by id} always answers at most one row because an id is unique. A find has to
 * earn the same promise, so the field it searches by must be unique — otherwise the flow would
 * assign a question with many answers to a single variable.
 */
class FindByTest {

    private static final String REPOSITORY =
            "src/main/java/com/example/account/repository/AccountRepository.java";
    private static final String SERVICE =
            "src/main/java/com/example/account/service/AccountService.java";

    @TempDir
    Path projectRoot;

    @Test
    void aFindBecomesADerivedFinderAndANotFound(@TempDir Path classes) throws IOException {
        project("- email: Email required unique", "account = find Account by email");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Instruction FIND_BY by email");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(REPOSITORY))
                .as("Spring Data reads the method name, so the name is the query")
                .contains("Optional<Account> findByEmail(String email);");
        assertThat(files.get(SERVICE))
                .contains("Account account = repository.findByEmail(request.email())")
                .as("a find that answers nothing is the same miss a load by id would report")
                .contains(".orElseThrow(() -> new NotFoundException(\"Account\", "
                        + "request.email()));");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aFindNeedsAUniqueFieldToAnswerAtMostOne() throws IOException {
        project("- email: Email required", "account = find Account by email");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_FIND_NOT_UNIQUE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("needs 'email' to be unique")
                        .contains("more than one record can answer"));
    }

    @Test
    void aFindNeedsAnInputToSearchWith() throws IOException {
        project("- email: Email required unique", "account = find Account by name");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .isNotEmpty()
                .anySatisfy(diagnostic ->
                        assertThat(diagnostic.message()).contains("has no input 'name'"));
    }

    @Test
    void aFindCannotNameAFieldTheEntityDoesNotHave() throws IOException {
        project("- email: Email required unique", "account = find Account by nickname");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_INPUT_FIELD_UNKNOWN))
                .isNotEmpty()
                .anySatisfy(diagnostic ->
                        assertThat(diagnostic.message()).contains("has no field 'nickname'"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(String emailField, String findLine) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/account.harpia.md"), """
                # Account

                ## Data

                - id: UUID generated
                """ + emailField + """

                - name: String required

                ## Query Find Account

                ### Endpoint

                GET /accounts

                ### Access

                public

                ### Input

                - email: Email required

                ### Flow

                ```flow
                """ + findLine + """

                return account
                ```

                ### Output

                200 Account

                ### Errors

                - not found -> 404
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: account-service
                  group: com.example
                  artifact: account-service
                  package: com.example.account

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
