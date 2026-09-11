package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.capability.Capability;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.target.TargetCatalog;
import dev.harpia.target.TargetConfiguration;
import dev.harpia.target.TargetStatus;
import dev.harpia.target.javaspring.mapping.JavaTypeMapper;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Everything Java, Spring and Maven specific is asserted here and nowhere upstream. */
class JavaSpringTargetTest {

    @Test
    void describesItselfAsTheOnlySupportedTarget() {
        JavaSpringTarget target = new JavaSpringTarget();

        assertThat(target.descriptor().id()).isEqualTo(TargetCatalog.JAVA_SPRING);
        assertThat(target.descriptor().status()).isEqualTo(TargetStatus.SUPPORTED);
        assertThat(target.descriptor().language()).isEqualTo("java");
        assertThat(target.descriptor().framework()).isEqualTo("spring-boot");
        assertThat(target.descriptor().capabilities())
                .containsExactlyInAnyOrder(
                        Capability.HTTP, Capability.PERSISTENCE, Capability.SECURITY);
        assertThat(target.descriptor().supportsLanguageVersion(21)).isTrue();
        assertThat(target.descriptor().supportsLanguageVersion(17)).isFalse();
    }

    @Test
    void mapsHarpiaTypesToJavaTypesOnlyInsideTheTarget() {
        assertThat(JavaTypeMapper.map(dev.harpia.application.ApplicationScalarType.DECIMAL).simpleName())
                .isEqualTo("BigDecimal");
        assertThat(JavaTypeMapper.map(dev.harpia.application.ApplicationScalarType.UUID).simpleName())
                .isEqualTo("UUID");
        assertThat(JavaTypeMapper.map(dev.harpia.application.ApplicationScalarType.EMAIL).simpleName())
                .isEqualTo("String");
        assertThat(JavaTypeMapper.map(dev.harpia.application.ApplicationScalarType.DATE_TIME).simpleName())
                .isEqualTo("OffsetDateTime");
    }

    @Test
    void derivesThePackageLayoutFromTheNamespace() {
        JavaLayout layout = JavaLayout.of("com.example.customer", "customer-service");

        assertThat(layout.packagePath()).isEqualTo("com/example/customer");
        assertThat(layout.packageName(JavaLayout.LOGIC)).isEqualTo("com.example.customer.logic");
        assertThat(layout.packagePath(JavaLayout.DOMAIN)).isEqualTo("com/example/customer/domain");
        assertThat(JavaLayout.repositoryTypeName("Customer")).isEqualTo("CustomerRepository");
        assertThat(layout.applicationClassName()).isEqualTo("CustomerServiceApplication");
    }

    @Test
    void requiresItsOwnBuildOptionInsteadOfLettingTheCoreValidateIt() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        new JavaSpringTarget().validate(
                dev.harpia.CustomerFixture.load().application(),
                configuration(Map.of()),
                diagnostics);

        assertThat(diagnostics.diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo(ErrorCodes.TARGET_OPTION);
                    assertThat(diagnostic.message()).contains("springBootVersion");
                });
    }

    @Test
    void acceptsTheCanonicalFixtureWhenItsOptionIsPresent() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        new JavaSpringTarget().validate(
                dev.harpia.CustomerFixture.load().application(),
                configuration(Map.of(JavaSpringTarget.SPRING_BOOT_VERSION_OPTION, "3.3.2")),
                diagnostics);

        assertThat(diagnostics.diagnostics()).isEmpty();
    }

    @Test
    void validatesJavaPackageRulesOnlyAfterTheTargetIsSelected() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        new JavaSpringTarget().validate(
                dev.harpia.CustomerFixture.load().application(),
                new TargetConfiguration(
                        TargetCatalog.JAVA_SPRING,
                        21,
                        "com.class",
                        "customer-service",
                        Map.of(JavaSpringTarget.SPRING_BOOT_VERSION_OPTION, "3.3.2")),
                diagnostics);

        assertThat(diagnostics.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(ErrorCodes.CONFIG_INVALID_PACKAGE);
            assertThat(diagnostic.message()).contains("project.group", "Java 21 package name");
        });
    }

    @Test
    void contributesSpringDependenciesAndConfigurationForTheResolvedCapabilities() {
        CompileResult result = new HarpiaCompiler().compile(
                new CompileRequest(Path.of("examples/customer")));

        String pom = result.tree().orElseThrow().files().get("pom.xml");
        assertThat(pom)
                .contains("<artifactId>spring-boot-starter-web</artifactId>")
                .contains("<artifactId>spring-boot-starter-data-jpa</artifactId>")
                .contains("<artifactId>flyway-database-postgresql</artifactId>")
                .contains("<version>3.3.6</version>");
        assertThat(result.tree().orElseThrow().files()
                        .get("src/main/resources/application.yaml"))
                .contains("spring.datasource.url: \"${DATABASE_URL}\"")
                .contains("spring.jpa.open-in-view: \"false\"");
    }

    private static TargetConfiguration configuration(Map<String, String> options) {
        return new TargetConfiguration(
                TargetCatalog.JAVA_SPRING, 21, "com.example", "customer-service", options);
    }
}
