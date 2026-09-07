package dev.harpia.diag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DiagnosticOrderingTest {

    private final List<Diagnostic> unordered = List.of(
            Diagnostic.warning("HRP5005", "symlink z", SourceRef.of("specs/z.harpia.md", 3, 2)),
            Diagnostic.error("HRP3005", "specs missing"),
            Diagnostic.error("HRP1004", "field b", SourceRef.of("specs/a.harpia.md", 8, 5)),
            Diagnostic.error("HRP1005", "type", SourceRef.of("specs/a.harpia.md", 8, 4)),
            Diagnostic.error("HRP3001", "config missing"),
            Diagnostic.warning("HRP1004", "field a", SourceRef.of("specs/a.harpia.md", 8, 5)),
            Diagnostic.error("HRP1004", "field a", SourceRef.of("specs/a.harpia.md", 8, 5)),
            Diagnostic.error("HRP1001", "heading", SourceRef.file("specs/a.harpia.md")),
            Diagnostic.error("HRP1001", "heading", SourceRef.of("specs/a.harpia.md", 1, 1)));

    @Test
    void ordersWithoutLocationFirstThenByLocationCodeMessageAndSeverity() {
        assertThat(DiagnosticOrdering.sorted(unordered))
                .containsExactly(
                        Diagnostic.error("HRP3001", "config missing"),
                        Diagnostic.error("HRP3005", "specs missing"),
                        Diagnostic.error("HRP1001", "heading", SourceRef.file("specs/a.harpia.md")),
                        Diagnostic.error("HRP1001", "heading", SourceRef.of("specs/a.harpia.md", 1, 1)),
                        Diagnostic.error("HRP1005", "type", SourceRef.of("specs/a.harpia.md", 8, 4)),
                        Diagnostic.error("HRP1004", "field a", SourceRef.of("specs/a.harpia.md", 8, 5)),
                        Diagnostic.warning("HRP1004", "field a", SourceRef.of("specs/a.harpia.md", 8, 5)),
                        Diagnostic.error("HRP1004", "field b", SourceRef.of("specs/a.harpia.md", 8, 5)),
                        Diagnostic.warning("HRP5005", "symlink z", SourceRef.of("specs/z.harpia.md", 3, 2)));
    }

    @Test
    void producesTheSameSequenceForShuffledInputs() {
        List<Diagnostic> expected = DiagnosticOrdering.sorted(unordered);

        for (int seed = 0; seed < 100; seed++) {
            List<Diagnostic> shuffled = new ArrayList<>(unordered);
            Collections.shuffle(shuffled, new Random(seed));

            assertThat(DiagnosticOrdering.sorted(shuffled))
                    .as("shuffle seed %s", seed)
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void comparatorOnlyTiesEqualDiagnostics() {
        for (Diagnostic left : unordered) {
            for (Diagnostic right : unordered) {
                if (!left.equals(right)) {
                    assertThat(DiagnosticOrdering.COMPARATOR.compare(left, right))
                            .as("%s compared with %s", left, right)
                            .isNotZero();
                }
            }
        }
    }

    @Test
    void returnsAnImmutableCopyWithoutMutatingTheInput() {
        List<Diagnostic> input = new ArrayList<>(unordered);
        List<Diagnostic> originalOrder = List.copyOf(input);

        List<Diagnostic> sorted = DiagnosticOrdering.sorted(input);

        assertThat(input).containsExactlyElementsOf(originalOrder);
        assertThatThrownBy(() -> sorted.add(Diagnostic.error("HRP9999", "must fail")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void collectorAlwaysExposesAnOrderedSnapshot() {
        DiagnosticCollector collector = new DiagnosticCollector();
        collector.addAll(unordered);

        assertThat(collector.diagnostics())
                .containsExactlyElementsOf(DiagnosticOrdering.sorted(unordered));
        assertThat(collector.hasErrors()).isTrue();
        assertThat(collector.size()).isEqualTo(unordered.size());
    }
}
