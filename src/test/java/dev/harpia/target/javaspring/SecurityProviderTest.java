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
 * How an identity is proved.
 *
 * <p>Which endpoints need one is what the specification says. How one arrives is a logical
 * provider, the way a database vendor is: the same declarations, a different deployment.
 */
class SecurityProviderTest {

    private static final String CONFIG = "src/main/java/com/example/auth/config/SecurityConfig.java";
    private static final String APP_CONFIG = "src/main/resources/application.yaml";
    private static final String TEST_CONFIG = "src/test/resources/application.yaml";

    @TempDir
    Path projectRoot;

    @Test
    void withoutAChoiceTheFrameworkGivesBasicForNothing() throws IOException {
        project(null);

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Capability security provider=basic");
        assertThat(result.tree().orElseThrow().files().get(CONFIG))
                .contains(".httpBasic(basic -> {})")
                .doesNotContain("oauth2ResourceServer");
        assertThat(result.tree().orElseThrow().files().get("pom.xml"))
                .doesNotContain("oauth2-resource-server");
    }

    @Test
    void aTokenIsTheSameChainReachedADifferentWay() throws IOException {
        project("jwt");

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Capability security provider=jwt");
        SortedMap<String, String> files = result.tree().orElseThrow().files();
        assertThat(files.get(CONFIG))
                .contains(".oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))")
                .as("which endpoints need an identity does not change with how one is proved")
                .contains(".requestMatchers(\"/customers\").permitAll()")
                .contains(".requestMatchers(\"/customers/{id}\").authenticated()");
        assertThat(files.get("pom.xml")).contains("spring-boot-starter-oauth2-resource-server");
    }

    @Test
    void theKeySetIsTheDeploymentsAnswerAndNotOneBakedIn() throws IOException {
        project("jwt");

        SortedMap<String, String> files = compile().tree().orElseThrow().files();
        assertThat(files.get(APP_CONFIG))
                .as("a URL baked into generated code is one environment written into every one")
                .contains("spring.security.oauth2.resourceserver.jwt.jwk-set-uri: "
                        + "\"${JWT_JWK_SET_URI}\"");
        assertThat(files.get(TEST_CONFIG))
                .as("a test has no deployment to fill the placeholder in")
                .contains("spring.security.oauth2.resourceserver.jwt.jwk-set-uri: "
                        + "\"http://localhost/.harpia-no-issuer/jwks.json\"");
    }

    @Test
    void aProjectWithNothingToSecureCarriesNoTestConfigurationEither() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        writeSpec("public");
        writeConfig("jwt");

        // The provider was named, but nothing asked for an identity, so nothing was resolved.
        CompileResult result = compile();
        assertThat(result.stages().application().orElseThrow().capabilities()
                        .requires(Capability.SECURITY))
                .isFalse();
        assertThat(result.tree().orElseThrow().files()).doesNotContainKey(TEST_CONFIG);
    }

    @Test
    void aMechanismNobodyImplementsIsRefused() throws IOException {
        project("kerberos");

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.CONFIG_UNSUPPORTED_VALUE))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("security.provider must be one of [basic, jwt]"));
    }

    @Test
    void theGeneratedProjectStartsWithNoEnvironmentAtAll(@TempDir Path generated) throws Exception {
        project("jwt");
        write(generated, compile().tree().orElseThrow().files());

        // Without the generated test configuration the context fails on an unresolved placeholder,
        // which only a real run of the generated project can show.
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

    private void project(String provider) throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        writeSpec("authenticated");
        writeConfig(provider);
    }

    private void writeSpec(String access) throws IOException {
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
    }

    private void writeConfig(String provider) throws IOException {
        String security = provider == null ? "" : """
                security:
                  provider: %s

                """.formatted(provider);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

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

                %sdatabase:
                  vendor: postgres

                paths:
                  specs: specs
                  output: generated

                generation:
                  migrations: true
                  tests: true
                """.formatted(security), StandardCharsets.UTF_8);
    }
}
