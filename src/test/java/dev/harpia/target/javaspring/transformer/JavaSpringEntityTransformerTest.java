package dev.harpia.target.javaspring.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CustomerFixture;
import dev.harpia.application.ApplicationEntity;
import dev.harpia.target.TargetCatalog;
import dev.harpia.target.TargetConfiguration;
import dev.harpia.target.javaspring.JavaSpringContext;
import dev.harpia.target.javaspring.JavaSpringTarget;
import dev.harpia.target.javaspring.model.JavaAnnotationModel;
import dev.harpia.target.javaspring.model.JavaFieldModel;
import dev.harpia.target.javaspring.model.JavaSourceFile;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaSpringEntityTransformerTest {

    @Test
    void resolvesEmailRequiredAndUniqueBeforeRendering() {
        CustomerFixture fixture = CustomerFixture.load();
        JavaSpringContext context = JavaSpringContext.of(
                fixture.application(),
                new TargetConfiguration(
                        TargetCatalog.JAVA_SPRING,
                        21,
                        fixture.config().project().group(),
                        fixture.config().project().artifact(),
                        Map.of(JavaSpringTarget.SPRING_BOOT_VERSION_OPTION, "3.3.6")));
        ApplicationEntity customer = fixture.application().entities().getFirst();

        JavaSourceFile source = new JavaSpringEntityTransformer().transform(context, customer);
        JavaFieldModel email = source.type().fields().stream()
                .filter(field -> field.name().equals("email"))
                .findFirst()
                .orElseThrow();

        assertThat(email.type().canonicalName()).isEqualTo("java.lang.String");
        assertThat(email.annotations())
                .extracting(annotation -> annotation.type().canonicalName())
                .containsExactly(
                        "jakarta.validation.constraints.Email",
                        "jakarta.validation.constraints.NotBlank",
                        "jakarta.persistence.Column");
        JavaAnnotationModel column = email.annotations().getLast();
        assertThat(column.attributes())
                .extracting(JavaAnnotationModel.Attribute::name, JavaAnnotationModel.Attribute::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("name", "\"email\""),
                        org.assertj.core.groups.Tuple.tuple("nullable", "false"),
                        org.assertj.core.groups.Tuple.tuple("unique", "true"),
                        org.assertj.core.groups.Tuple.tuple("length", "320"));
        assertThat(source.source()).contains(customer.where());
    }
}
