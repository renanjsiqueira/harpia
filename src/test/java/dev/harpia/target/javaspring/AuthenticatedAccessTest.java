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
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code authenticated} access.
 *
 * <p>It says the request must carry an identity, and nothing about how it proves one: which
 * mechanism supplies the identity is a provider's decision, the way a database vendor is. What the
 * specification decides is which endpoints are reachable without one.
 */
class AuthenticatedAccessTest {

    private static final String CONFIG = "src/main/java/com/example/auth/config/SecurityConfig.java";
    private static final String CONTROLLER_TEST =
            "src/test/java/com/example/auth/web/CustomerControllerTest.java";

    @TempDir
    Path projectRoot;

    @Test
    void demandingAnIdentityRequiresSomethingToCheckOne() throws IOException {
        project(1, "authenticated");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.stages().application().orElseThrow().capabilities()
                        .requires(Capability.SECURITY))
                .isTrue();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("access=AUTHENTICATED")
                .as("the target enforces access itself, the way it exposes endpoints itself")
                .contains("Capability security provider=<target>");
    }

    @Test
    void eachDeclaredRouteBecomesARuleAndTheRestIsRefused() throws IOException {
        project(1, "authenticated");

        String config = compile().tree().orElseThrow().files().get(CONFIG);
        assertThat(config)
                .contains(".requestMatchers(\"/customers\").permitAll()")
                .contains(".requestMatchers(\"/customers/{id}\").authenticated()")
                .as("the default only catches a path nobody declared, and refusing those is safer")
                .contains(".anyRequest().denyAll()")
                .as("a stateless API has no session to ride on and no cookie for CSRF to protect")
                .contains(".csrf(csrf -> csrf.disable())")
                .contains("SessionCreationPolicy.STATELESS");
    }

    @Test
    void aProjectWithNothingToEnforceGetsNoSecurityAtAll() throws IOException {
        project(1, "public");

        CompileResult result = compile();
        assertThat(result.stages().application().orElseThrow().capabilities()
                        .requires(Capability.SECURITY))
                .isFalse();
        assertThat(result.tree().orElseThrow().files()).doesNotContainKey(CONFIG);
        assertThat(result.tree().orElseThrow().files().get("pom.xml"))
                .as("a dependency changes how a project starts; nobody asked for that here")
                .doesNotContain("spring-boot-starter-security");
    }

    @Test
    void theGeneratedTestProvesTheRefusalRatherThanAssertingItsDeclaredStatus() throws IOException {
        project(1, "authenticated");

        String test = compile().tree().orElseThrow().files().get(CONTROLLER_TEST);
        assertThat(test)
                .as("a slice test would otherwise be judged by Spring Boot's default rule")
                .contains("@Import(SecurityConfig.class)")
                .contains("void deleteCustomerRefusesAnAnonymousRequest()")
                .as("401 and not 403: the request carried no identity at all")
                .contains("status().is(401)")
                .as("an anonymous request cannot reach the declared outcome")
                .doesNotContain("void deleteCustomerReturnsItsDeclaredStatus()");
        assertThat(test)
                .as("a public endpoint is still exercised the way it always was")
                .contains("void listCustomersReturnsItsDeclaredStatus()");
    }

    @Test
    void v0HasOnlyPublicEndpoints() throws IOException {
        project(0, "authenticated");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.UNSUPPORTED_AUTHENTICATION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .isEqualTo("access 'authenticated' needs harpia.languageVersion 1"));
    }

    @Test
    void theGeneratedProjectEnforcesWhatItDeclared(@TempDir Path generated) throws Exception {
        project(1, "authenticated");
        write(generated, compile().tree().orElseThrow().files());

        // Only the generated project can answer this: a string says the rule was written down,
        // a running filter chain says it is applied.
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

                database:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """.formatted(languageVersion), StandardCharsets.UTF_8);
    }
}
