package dev.harpia.target.javaspring;

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
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How an outbound call proves who is calling.
 *
 * <p>The scheme is a binding: it is part of how the other side is reached, and the specification
 * that calls the port never mentions it. The credential is not a binding at all — it differs per
 * deployment and it is a secret, so it stays in configuration and the generated project reads it
 * from the environment.
 */
class IntegrationAuthTest {

    private static final String CLIENT =
            "src/main/java/com/example/order/integration/FraudServiceClient.java";
    private static final String APP_CONFIG = "src/main/resources/application.yaml";
    private static final String TEST_CONFIG = "src/test/resources/application.yaml";

    @TempDir
    Path projectRoot;

    @BeforeEach
    void writeProject() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # Purchase

                ## Integration FraudService

                ### Operation CheckOrder

                #### Input

                - orderId: UUID required

                #### Output

                Boolean

                ## Data

                - id: UUID generated
                - orderId: UUID required

                ## Command CreateOrder

                ### Input

                - orderId: UUID required

                ### Flow

                ```flow
                approved = call FraudService.CheckOrder(orderId = orderId)
                purchase = create Purchase from input
                save purchase
                return purchase
                ```

                ### Output

                201 Purchase
                """, StandardCharsets.UTF_8);
        config();
    }

    @Test
    void aBearerCredentialTravelsInTheHeaderTheSchemeOwns() throws IOException {
        bindings("bindings/http.harpia.md", bindings("bearer"));

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Http POST https://fraud.example/checks/{orderId} auth=bearer");
        assertThat(result.tree().orElseThrow().files().get(CLIENT))
                .as("one place reads the credential, and no operation can forget to send it")
                .contains("this.client = builder.defaultHeader(\"Authorization\", "
                        + "\"Bearer \" + credential).build();");
    }

    @Test
    void anApiKeyIsTheValueAndNothingElse() throws IOException {
        bindings("bindings/http.harpia.md", bindings("api key X-Api-Key"));

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("auth=api-key X-Api-Key");
        assertThat(result.tree().orElseThrow().files().get(CLIENT))
                .as("the prefix belongs to bearer; an API key is sent as it was issued")
                .contains("this.client = builder.defaultHeader(\"X-Api-Key\", credential)"
                        + ".build();");
    }

    @Test
    void theCredentialIsConfigurationAndNeverADeclaration() throws IOException {
        bindings("bindings/http.harpia.md", bindings("bearer"));

        SortedMap<String, String> files = compile().tree().orElseThrow().files();
        assertThat(files.get(CLIENT))
                .contains("@Value(\"${harpia.integration.fraud-service.credential}\")");
        assertThat(files.get(APP_CONFIG))
                .as("a secret written into generated source is a secret in version control")
                .contains("harpia.integration.fraud-service.credential: "
                        + "\"${FRAUD_SERVICE_CREDENTIAL}\"");
        assertThat(files.get(TEST_CONFIG))
                .as("a test has no deployment to fill the placeholder in")
                .contains("harpia.integration.fraud-service.credential: "
                        + "\"harpia-test-credential\"");
        assertThat(files.get(APP_CONFIG))
                .as("nothing generated should look like it shipped with a credential")
                .doesNotContain("harpia-test-credential");
    }

    @Test
    void aPortThatAsksForNothingCarriesNoCredentialAtAll() throws IOException {
        bindings("bindings/http.harpia.md", bindings(null));

        SortedMap<String, String> files = compile().tree().orElseThrow().files();
        assertThat(files.get(CLIENT))
                .contains("this.client = builder.build();")
                .doesNotContain("@Value")
                .doesNotContain("credential");
        assertThat(files.get(APP_CONFIG)).doesNotContain("credential");
        assertThat(files).doesNotContainKey(TEST_CONFIG);
    }

    @Test
    void anAuthNobodyImplementsIsRefused() throws IOException {
        bindings("bindings/http.harpia.md", bindings("oauth2"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SYNTAX_BINDING_SECTION))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .isEqualTo("'## Auth' requires 'bearer' or 'api key <Header-Name>'"));
    }

    @Test
    void onePortIsReachedOneWay() throws IOException {
        bindings("bindings/check.harpia.md", """
                # HTTP Bindings

                ## Base URL

                https://fraud.example

                ## Auth

                bearer

                ## Bind FraudService.CheckOrder

                ### Endpoint

                POST /checks/{orderId}

                ### Request

                - orderId: path orderId

                ### Response

                output: body
                """);
        bindings("bindings/notify.harpia.md", """
                # HTTP Bindings

                ## Base URL

                https://fraud.example

                ## Auth

                api key X-Api-Key

                ## Bind FraudService.Notify

                ### Endpoint

                POST /notifications/{orderId}

                ### Request

                - orderId: path orderId

                ### Response

                none
                """);
        Files.writeString(
                projectRoot.resolve("specs/order.harpia.md"),
                Files.readString(projectRoot.resolve("specs/order.harpia.md"),
                                StandardCharsets.UTF_8)
                        .replace("## Data", """
                                ### Operation Notify

                                #### Input

                                - orderId: UUID required

                                #### Output

                                nothing

                                ## Data"""),
                StandardCharsets.UTF_8);

        // The generated client is one object with one credential, so one of the two declarations
        // would have to be dropped without saying so.
        assertThat(compile().diagnostics())
                .filteredOn(diagnostic ->
                        diagnostic.code().equals(ErrorCodes.SEMANTIC_BINDING_AUTH))
                .singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.message())
                        .contains("Integration 'FraudService' is bound with")
                        .contains("bearer")
                        .contains("api key X-Api-Key"));
    }

    @Test
    void theAuthenticatedClientCompiles(@TempDir Path classes) throws IOException {
        bindings("bindings/http.harpia.md", bindings("bearer"));

        GeneratedJava.compiles(compile().tree().orElseThrow().files(), classes);
    }

    @Test
    void theRunningClientSendsTheCredentialOnEveryCall(@TempDir Path generated) throws Exception {
        bindings("bindings/http.harpia.md", bindings("bearer"));
        write(generated, compile().tree().orElseThrow().files());
        // A generated string says the header was written down. Only a call that leaves the client
        // says it is sent — and the generated project is the only place that call can happen.
        probe(generated, """
                package com.example.order.integration;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.springframework.test.web.client.match.MockRestRequestMatchers\
                        .header;
                import static org.springframework.test.web.client.match.MockRestRequestMatchers\
                        .requestTo;
                import static org.springframework.test.web.client.response\
                        .MockRestResponseCreators.withSuccess;

                import jakarta.validation.Validation;
                import java.util.UUID;
                import org.junit.jupiter.api.Test;
                import org.springframework.http.MediaType;
                import org.springframework.test.web.client.MockRestServiceServer;
                import org.springframework.web.client.RestClient;

                class CredentialProbeTest {

                    @Test
                    void theCredentialTravelsWithTheCall() {
                        RestClient.Builder builder = RestClient.builder();
                        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                                .build();
                        FraudServiceClient client = new FraudServiceClient(
                                builder,
                                Validation.buildDefaultValidatorFactory().getValidator(),
                                "s3cr3t");
                        UUID orderId = UUID.fromString(
                                "00000000-0000-0000-0000-000000000001");
                        server.expect(requestTo(
                                        "https://fraud.example/checks/" + orderId))
                                .andExpect(header("Authorization", "Bearer s3cr3t"))
                                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

                        assertThat(client.checkOrder(orderId)).isTrue();
                        server.verify();
                    }
                }
                """);

        assertThat(maven(generated)).isZero();
    }

    @Test
    void aGeneratedTestNeverCarriesTheDeploymentsPlaceholder(@TempDir Path generated)
            throws Exception {
        bindings("bindings/http.harpia.md", bindings("bearer"));
        write(generated, compile().tree().orElseThrow().files());
        // Spring leaves an unresolvable nested placeholder as it found it, so without the
        // generated test configuration the credential is not absent — it is the literal string
        // "${FRAUD_SERVICE_CREDENTIAL}", quietly sent as if it were a secret. Deleting
        // src/test/resources/application.yaml here makes this test fail, which is the reason the
        // file is generated.
        probe(generated, """
                package com.example.order.integration;

                import static org.assertj.core.api.Assertions.assertThat;

                import jakarta.validation.Validation;
                import jakarta.validation.Validator;
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Import;
                import org.springframework.web.client.RestClient;

                @SpringBootTest(classes = CredentialProbeTest.Config.class)
                class CredentialProbeTest {

                    @Configuration
                    @Import(FraudServiceClient.class)
                    static class Config {

                        @Bean
                        RestClient.Builder builder() {
                            return RestClient.builder();
                        }

                        @Bean
                        Validator validator() {
                            return Validation.buildDefaultValidatorFactory().getValidator();
                        }
                    }

                    @Value("${harpia.integration.fraud-service.credential}")
                    String credential;

                    @Test
                    void theTestsHaveACredentialOfTheirOwn() {
                        assertThat(credential).isEqualTo("harpia-test-credential");
                    }
                }
                """);

        assertThat(maven(generated)).isZero();
    }

    private static void probe(Path root, String source) throws IOException {
        Path file = root.resolve(
                "src/test/java/com/example/order/integration/CredentialProbeTest.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static int maven(Path root) throws Exception {
        Path log = root.resolve("maven-test.log");
        Process process = new ProcessBuilder("mvn", "-q", "-o", "test")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("generated Maven build timed out");
        }
        return process.exitValue();
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

    private void bindings(String path, String source) throws IOException {
        Path file = projectRoot.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static String bindings(String auth) {
        String section = auth == null ? "" : """
                ## Auth

                %s

                """.formatted(auth);
        return """
                # HTTP Bindings

                ## Base URL

                https://fraud.example

                %s## Bind FraudService.CheckOrder

                ### Endpoint

                POST /checks/{orderId}

                ### Request

                - orderId: path orderId

                ### Response

                output: body
                """.formatted(section);
    }

    private void config() throws IOException {
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
                    springBootVersion: "3.4.4"

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
