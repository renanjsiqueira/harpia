package dev.harpia.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.config.ConfigLoader;
import dev.harpia.config.HarpiaConfig;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.parse.SpecAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.source.SourceFile;
import dev.harpia.validate.SemanticValidator;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolverTest {

    @Test
    void resolvesCanonicalNamesTypesAndFlowVariables() {
        Path root = Path.of("examples/customer").toAbsolutePath().normalize();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        HarpiaConfig config = ConfigLoader.load(root, diagnostics).orElseThrow();
        SourceFile source = SourceFile.read(
                        root,
                        root.resolve("specs/customer.harpia.md"),
                        diagnostics)
                .orElseThrow();
        SpecAst specification = SpecParser.parse(source, diagnostics).orElseThrow();
        SemanticValidator.validate(List.of(specification), diagnostics);

        ProjectModel project = Resolver.resolve(List.of(specification), List.of(), List.of());

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(project.entities()).singleElement().satisfies(entity -> {
            assertThat(entity.name()).isEqualTo("Customer");
            assertThat(entity.idField().type()).isEqualTo(TypeRef.UUID);
            assertThat(entity.fields())
                    .extracting(FieldModel::name)
                    .containsExactly("id", "name", "email", "active");
            assertThat(entity.useCases())
                    .extracting(UseCaseModel::baseName)
                    .containsExactly(
                            "CreateCustomer",
                            "GetCustomer",
                            "ListCustomers",
                            "UpdateCustomer",
                            "DeleteCustomer");
            assertThat(entity.useCases().get(2).flow().variables())
                    .containsEntry(
                            "customers",
                            new FlowModel.ValueType(FlowModel.Kind.LIST, "Customer"));
        });
    }
}
