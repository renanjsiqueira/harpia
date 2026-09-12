package dev.harpia.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.config.ConfigLoader;
import dev.harpia.config.HarpiaConfig;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.parse.ModuleAst;
import dev.harpia.parse.ProjectAst;
import dev.harpia.parse.SpecParser;
import dev.harpia.source.SourceFile;
import dev.harpia.symbol.SymbolTable;
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
        ModuleAst module = SpecParser.parse(
                source, config.harpia().languageVersion(), diagnostics).orElseThrow();
        ProjectAst syntax = new ProjectAst(config.harpia().languageVersion(), List.of(module));
        SymbolTable symbols = SymbolTable.declare(syntax, diagnostics);
        dev.harpia.binding.BindingModel bindings =
                dev.harpia.binding.BindingResolver.resolve(syntax, symbols, diagnostics);
        dev.harpia.binding.BindingValidator.validate(syntax, bindings, diagnostics);
        SemanticValidator.validate(
                syntax,
                symbols,
                bindings,
                diagnostics);

        ProjectModel project = Resolver.resolve(
                syntax,
                bindings,
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of());

        assertThat(diagnostics.diagnostics()).isEmpty();
        assertThat(project.entities()).singleElement().satisfies(entity -> {
            assertThat(entity.name()).isEqualTo("Customer");
            assertThat(entity.idField().type()).isEqualTo(FieldType.scalar(TypeRef.UUID));
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
