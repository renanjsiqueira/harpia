package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.capability.Capability;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.inspect.Inspector;
import dev.harpia.inspect.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code role} access.
 *
 * <p>{@code authenticated} says the request must carry an identity; {@code role} says the identity
 * must also be a particular kind of person. Listing several is how you say a thing is open to more
 * than one kind, so the check is "any of".
 */
class RoleAccessTest {

    private static final String CONFIG = "src/main/java/com/example/auth/config/SecurityConfig.java";

    @TempDir
    Path projectRoot;

    @Test
    void aRoleIsNamedTheWayTheSpecificationNamesEverythingElse() throws IOException {
        project(1, "role admin");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Access ROLE admin");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("access=ROLE admin");
        assertThat(result.tree().orElseThrow().files().get(CONFIG))
                .as("the upper-case authority is the framework's spelling of the same thing")
                .contains(".requestMatchers(HttpMethod.DELETE, \"/customers/{id}\")"
                        + ".hasAnyRole(\"ADMIN\")");
    }

    @Test
    void listingSeveralRolesMeansAnyOfThem() throws IOException {
        project(1, "role admin or auditor");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("access=ROLE admin or auditor");
        assertThat(result.tree().orElseThrow().files().get(CONFIG))
                .contains(".hasAnyRole(\"ADMIN\", \"AUDITOR\")");
    }

    @Test
    void demandingARoleAlsoDemandsAnIdentity() throws IOException {
        project(1, "role admin");

        assertThat(compile().stages().application().orElseThrow().capabilities()
                        .requires(Capability.SECURITY))
                .as("a role is asked of somebody, so somebody has to be there first")
                .isTrue();
    }

    @Test
    void aScopeIsSpelledByWhoeverIssuesTheToken() throws IOException {
        project(1, "scope orders:read or orders:write", "jwt");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("access=SCOPE orders:read or orders:write");
        assertThat(result.tree().orElseThrow().files().get(CONFIG))
                .as("SCOPE_ is the prefix Spring reads; the rest is the issuer's word, not ours")
                .contains(".hasAnyAuthority(\"SCOPE_orders:read\", \"SCOPE_orders:write\")");
    }

    @Test
    void aScopeWithoutATokenIsRefused() throws IOException {
        project(1, "scope orders:read", "basic");

        // With basic authentication there are no scopes to carry, so the rule would match nothing
        // and every request would be refused: an access rule that reads open and behaves closed.
        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.CAPABILITY_PROVIDER_UNSUPPORTED))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("access 'scope orders:read' needs a token")
                        .contains("which carries no scopes"));
    }

    @Test
    void v0HasOnlyPublicEndpoints() throws IOException {
        project(0, "role admin");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.UNSUPPORTED_AUTHENTICATION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .isEqualTo("access 'role admin' needs harpia.languageVersion 1"));
    }

    @Test
    void aRoleThatIsNotANameIsRefused() throws IOException {
        project(1, "role Admin");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.UNSUPPORTED_AUTHENTICATION))
                .isNotEmpty();
    }

    @Test
    void theGeneratedProjectEnforcesWhatItDeclared(@TempDir Path generated) throws Exception {
        project(1, "role admin");
        write(generated, compile().tree().orElseThrow().files());

        // A string says the rule was written down; a running filter chain says it is applied.
        Path log = generated.resolve("maven-test.log");
        Process process = new ProcessBuilder("mvn", "-q", "-o", "test")
                .directory(generated.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = Files.exists(log)
                ? Files.readString(log, StandardCharsets.UTF_8)
                : "Maven produced no log";
        assertThat(finished).as("generated Maven build timed out: %s", output).isTrue();
        assertThat(process.exitValue()).as("generated Maven build failed: %s", output).isZero();
    }

    private static void write(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
        }
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void project(int languageVersion, String access) throws IOException {
        project(languageVersion, access, "basic");
    }

    private void project(int languageVersion, String access, String provider) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - name: String required

                ## List Customers

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

                ## Delete Customer

                ### Endpoint

                DELETE /customers/{id}

                ### Access

                %s

                ### Flow

                ```flow
                customer = load Customer by id
                delete customer
                return nothing
                ```

                ### Output

                204 nothing

                ### Errors

                - not found -> 404
                """.formatted(access), StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: %d

                project:
                  name: auth-service
                  group: com.example
                  artifact: auth-service
                  package: com.example.auth

                target:
                  id: java-spring
                  language:
                    version: 21
                  options:
                    springBootVersion: "3.4.4"

                security:
                  provider: %s

                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """.formatted(languageVersion, provider), StandardCharsets.UTF_8);
    }
}
