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
import java.util.SortedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end contract of the first outbound HTTP provider. */
class IntegrationHttpClientTest {

    private static final String CLIENT =
            "src/main/java/com/example/order/integration/FraudServiceClient.java";

    @TempDir
    Path projectRoot;

    @BeforeEach
    void writeProject() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/order.harpia.md"), """
                # Purchase

                ## Value FraudContext

                - fingerprint: String required

                ## Integration FraudService

                ### Operation CheckOrder

                #### Input

                - orderId: UUID required
                - context: FraudContext required

                #### Output

                Boolean

                ### Operation Notify

                #### Input

                - orderId: UUID required

                #### Output

                nothing

                ### Operation Search

                #### Input

                - term: String required
                - tenant: String required

                #### Output

                List<Boolean>

                ## Data

                - id: UUID generated
                - orderId: UUID required
                - context: FraudContext required

                ## Command CreateOrder

                ### Input

                - orderId: UUID required
                - context: FraudContext required

                ### Flow

                ```flow
                approved = call FraudService.CheckOrder(
                    context = context,
                    orderId = orderId
                )
                call FraudService.Notify(orderId = orderId)
                purchase = create Purchase from input
                save purchase
                return purchase
                ```

                ### Output

                201 Purchase
                """, StandardCharsets.UTF_8);
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

    @Test
    void aResponseIsCheckedAgainstTheContractThePortDeclared(@TempDir Path classes)
            throws IOException {
        bindings(validBindings());

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        SortedMap<String, String> files = result.tree().orElseThrow().files();

        // A body that does not satisfy the declaration is not a smaller answer; it is not an
        // answer. Without this a missing required field arrives as null and the flow carries on.
        assertThat(files.get(CLIENT))
                .contains("private void check(String operation, Object body) {")
                .contains("throw new FraudServiceException(operation, "
                        + "\"the response carried no body\");")
                .as("the same bad response has to produce the same message every time")
                .contains(".sorted()")
                .contains("check(\"checkOrder\", answer);")
                .contains("return answer;");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void everyCallCarriesADeadlineWithoutMakingTheClientUntestable(@TempDir Path classes)
            throws IOException {
        bindings(validBindings());

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        SortedMap<String, String> files = result.tree().orElseThrow().files();

        // A call that can hang forever is not a dependency, it is a thread held until something
        // else gives up. The deadline is a customizer because setting a request factory inside the
        // client would silently replace the one MockRestServiceServer installs.
        assertThat(files.get(
                        "src/main/java/com/example/order/integration/IntegrationTimeouts.java"))
                .contains("public RestClientCustomizer harpiaIntegrationTimeouts(")
                .contains("@Value(\"${harpia.integration.connect-timeout:2s}\")")
                .contains("@Value(\"${harpia.integration.read-timeout:10s}\")")
                .contains("requestFactory.setConnectTimeout(connectTimeout);")
                .contains("requestFactory.setReadTimeout(readTimeout);");
        assertThat(files.get(CLIENT))
                .as("the client takes the builder as Spring hands it over, and nothing else")
                .contains("this.client = builder.build();")
                .doesNotContain("SimpleClientHttpRequestFactory");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void aFailedCallRaisesThePortsOwnExceptionAndNotSprings(@TempDir Path classes)
            throws IOException {
        bindings(validBindings());

        CompileResult result = compile();
        assertThat(result.diagnostics()).isEmpty();
        SortedMap<String, String> files = result.tree().orElseThrow().files();

        // An Integration exists so the caller does not have to know how the other side is reached,
        // and an exception is part of what a caller has to know.
        assertThat(files.get(
                        "src/main/java/com/example/order/integration/FraudServiceException.java"))
                .contains("public class FraudServiceException extends RuntimeException")
                .contains("public FraudServiceException(String operation, int status)")
                .contains("public FraudServiceException(String operation, Throwable cause)")
                .as("a call that never got a response failed just as truly")
                .contains("return Optional.ofNullable(status);");
        assertThat(files.get(CLIENT))
                .contains(".onStatus(HttpStatusCode::isError, (request, response) -> {")
                .contains("throw new FraudServiceException(\"notify\", "
                        + "response.getStatusCode().value());")
                .contains("} catch (ResourceAccessException exception) {")
                .contains("throw new FraudServiceException(\"notify\", exception);");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void bindingGeneratesTypedRestClientAndInjectsItOnlyWhereUsed(@TempDir Path classes)
            throws IOException {
        bindings(validBindings());

        CompileResult result = compile();

        assertThat(result.diagnostics()).as(result.diagnostics().toString()).isEmpty();
        assertThat(Inspector.render(result, Stage.AST).orElseThrow())
                .contains("IntegrationHttp FraudService.CheckOrder POST /checks/{orderId}")
                .contains("IntegrationHttp FraudService.Notify POST /notifications/{orderId}");
        assertThat(Inspector.render(result, Stage.BUSINESS_IR).orElseThrow())
                .contains("Http POST https://fraud.example/checks/{orderId}");
        assertThat(Inspector.render(result, Stage.APPLICATION_IR).orElseThrow())
                .contains("Capability http provider=<target>");

        SortedMap<String, String> files = result.tree().orElseThrow().files();
        String client = files.get(
                "src/main/java/com/example/order/integration/FraudServiceClient.java");
        assertThat(client)
                .contains("public class FraudServiceClient")
                .contains("RestClient client")
                .contains("public Boolean checkOrder(UUID orderId, FraudContext context)")
                .contains("UriComponentsBuilder.fromUriString("
                        + "\"https://fraud.example/checks/{orderId}\")")
                .contains(".buildAndExpand(orderId)")
                .contains("body.put(\"context\", context);")
                .contains("Boolean answer = client.post()")
                .contains(".body(Boolean.class);")
                .contains("public void notify(UUID orderId)")
                .contains(".toBodilessEntity();")
                .contains("public List<Boolean> search(String term, String tenant)")
                .contains(".queryParam(\"q\", term)")
                .contains(".header(\"X-Tenant\", String.valueOf(tenant))")
                .contains("new ParameterizedTypeReference<List<Boolean>>() {}");

        String service = files.get(
                "src/main/java/com/example/order/service/PurchaseService.java");
        assertThat(service)
                .contains("private final FraudServiceClient fraudServiceClient;")
                .contains("PurchaseRepository repository, FraudServiceClient fraudServiceClient")
                .contains("Boolean approved = fraudServiceClient.checkOrder("
                        + "request.orderId(), request.context());")
                .contains("fraudServiceClient.notify(request.orderId());");
        assertThat(files.get("pom.xml"))
                .contains("spring-boot-starter-web");

        GeneratedJava.compiles(files, classes);
    }

    @Test
    void outboundBindingsRejectInboundAccessSections() throws IOException {
        bindings(validBindings().replace("### Request\n\n- orderId: path orderId",
                "### Access\n\npublic\n\n### Request\n\n- orderId: path orderId"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SYNTAX_BINDING_SECTION))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("cannot declare '### Access'"));
    }

    @Test
    void anOutboundBaseUrlMustBeAbsolute() throws IOException {
        bindings(validBindings().replace("https://fraud.example", "/fraud"));

        assertThat(compile().diagnostics())
                .filteredOn(diagnostic -> diagnostic.code()
                        .equals(ErrorCodes.SEMANTIC_BINDING_MAPPING))
                .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                        .contains("requires an absolute http(s)"));
    }

    private CompileResult compile() {
        return new HarpiaCompiler().compile(new CompileRequest(projectRoot));
    }

    private void bindings(String source) throws IOException {
        Files.createDirectories(projectRoot.resolve("bindings"));
        Files.writeString(
                projectRoot.resolve("bindings/http.harpia.md"),
                source,
                StandardCharsets.UTF_8);
    }

    private static String validBindings() {
        return """
                # HTTP Bindings

                ## Base URL

                https://fraud.example

                ## Bind FraudService.CheckOrder

                ### Endpoint

                POST /checks/{orderId}

                ### Request

                - orderId: path orderId
                - input: body

                ### Response

                output: body

                ## Bind FraudService.Notify

                ### Endpoint

                POST /notifications/{orderId}

                ### Request

                - orderId: path orderId

                ### Response

                none

                ## Bind FraudService.Search

                ### Endpoint

                GET /checks

                ### Request

                - term: query q
                - tenant: header X-Tenant

                ### Response

                output: body
                """;
    }
}
