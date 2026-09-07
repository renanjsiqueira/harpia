package dev.harpia.target.javaspring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.target.TargetGenerationException;
import org.junit.jupiter.api.Test;

class JavaSpringTemplateRulesTest {

    private static final Path TEMPLATES =
            Path.of("src/main/resources/targets/java-spring/templates");

    @Test
    void targetPackContainsOnlyDeclarativeTemplates() throws IOException {
        assertThat(Files.list(TEMPLATES)
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString()))
                .containsExactlyInAnyOrder(
                        "application.yaml.mustache", "migration.sql.mustache", "pom.xml.mustache");

        try (var files = Files.list(TEMPLATES)) {
            for (Path template : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(template, StandardCharsets.UTF_8);
                assertThat(source)
                        .as(template.toString())
                        .doesNotContain("isRequired")
                        .doesNotContain("isEmail")
                        .doesNotContain("required")
                        .doesNotContain("unique")
                        .doesNotContain("now")
                        .doesNotContain("timestamp");
            }
        }
    }

    @Test
    void missingTemplateBecomesAStableTargetDiagnosticFailure() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new JavaSpringTemplates().render("missing.mustache", Map.of()))
                .isInstanceOfSatisfying(TargetGenerationException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCodes.TARGET_TEMPLATE_FAILURE);
                    assertThat(failure.getMessage()).contains("missing.mustache");
                });
    }
}
