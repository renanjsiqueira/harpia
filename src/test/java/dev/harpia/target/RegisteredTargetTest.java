package dev.harpia.target;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.CompileRequest;
import dev.harpia.CompileResult;
import dev.harpia.HarpiaCompiler;
import dev.harpia.application.ApplicationProject;
import dev.harpia.capability.Capability;
import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.emit.GeneratedFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A target the built-in catalogue has never heard of.
 *
 * <p>{@link TargetCatalog} documents intent: which targets Harpia plans to ship. It is not the list
 * of what the compiler can do, and this test is what keeps the two apart — a registry assembled by
 * a future plugin loader must be able to generate without anyone editing a built-in list first.
 */
class RegisteredTargetTest {

    private static final TargetId ID = TargetId.of("acme-runtime");

    @TempDir
    Path projectRoot;

    @BeforeEach
    void writeProject() throws IOException {
        Files.createDirectories(projectRoot.resolve("specs"));
        Files.writeString(projectRoot.resolve("specs/customer.harpia.md"), """
                # Customer

                ## Data

                - id: UUID generated
                - email: Email required unique

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
                """, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("harpia.yaml"), """
                harpia:
                  schemaVersion: 1
                  languageVersion: 1

                project:
                  name: customer-service
                  group: com.example
                  artifact: customer-service
                  package: com.example.customer

                target:
                  id: acme-runtime
                  language:
                    version: 21

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
    void aTargetOutsideTheCatalogueGeneratesWhenItIsRegistered() {
        CompileResult result = compileWith(new AcmeTarget());

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tree().orElseThrow().files())
                .containsEntry("acme/customer.acme", "entity Customer\n");
    }

    @Test
    void theSameTargetIsUnknownWhenNothingRegistersIt() {
        CompileResult result = compileWith();

        assertThat(result.tree()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(dev.harpia.diag.ErrorCodes.TARGET_UNKNOWN);
            assertThat(diagnostic.message()).contains("unknown target `acme-runtime`");
        });
    }

    @Test
    void theHintNamesWhatTheRegistryHoldsRatherThanWhatTheCatalogueLists() {
        CompileResult result = compileWith(new AcmeTarget(), new UnknownIdTarget());

        assertThat(result.diagnostics()).isEmpty();

        CompileResult unknown = compileWith(new UnknownIdTarget());
        assertThat(unknown.diagnostics()).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.hint()).hasValueSatisfying(hint -> assertThat(hint)
                        .as("a registered target is supported; java-spring is not registered here")
                        .contains("supported targets: [other-runtime]")
                        .doesNotContain("java-spring")));
    }

    private CompileResult compileWith(HarpiaTarget... targets) {
        return new HarpiaCompiler(new TargetRegistry(List.of(targets)))
                .compile(new CompileRequest(projectRoot));
    }

    /** A target that exists only in this test, with an identifier no catalogue entry uses. */
    private static final class AcmeTarget implements HarpiaTarget {

        @Override
        public TargetDescriptor descriptor() {
            return new TargetDescriptor(
                    ID,
                    "Acme Runtime",
                    "acme",
                    "acme-runtime",
                    TargetStatus.SUPPORTED,
                    "Acme >= 1",
                    1,
                    Set.of(Capability.HTTP, Capability.PERSISTENCE));
        }

        @Override
        public void validate(
                ApplicationProject application,
                TargetConfiguration configuration,
                DiagnosticCollector diagnostics) {
        }

        @Override
        public TargetGenerationResult generate(
                ApplicationProject application, TargetConfiguration configuration) {
            List<GeneratedFile> files = application.entities().stream()
                    .map(entity -> GeneratedFile.other(
                            "acme/" + entity.typeName().toLowerCase(java.util.Locale.ROOT) + ".acme",
                            "entity " + entity.typeName() + "\n"))
                    .toList();
            return new TargetGenerationResult(files);
        }
    }

    /** A second registered target, so the hint has something to list that is not the one asked for. */
    private static final class UnknownIdTarget extends ForwardingTarget {

        private UnknownIdTarget() {
            super(TargetId.of("other-runtime"));
        }
    }

    private abstract static class ForwardingTarget implements HarpiaTarget {

        private final TargetId id;

        private ForwardingTarget(TargetId id) {
            this.id = id;
        }

        @Override
        public TargetDescriptor descriptor() {
            return new TargetDescriptor(
                    id,
                    "Other Runtime",
                    "other",
                    "other-runtime",
                    TargetStatus.SUPPORTED,
                    "Other >= 1",
                    1,
                    Set.of(Capability.HTTP, Capability.PERSISTENCE));
        }

        @Override
        public void validate(
                ApplicationProject application,
                TargetConfiguration configuration,
                DiagnosticCollector diagnostics) {
        }

        @Override
        public TargetGenerationResult generate(
                ApplicationProject application, TargetConfiguration configuration) {
            return new TargetGenerationResult(List.of());
        }
    }
}
