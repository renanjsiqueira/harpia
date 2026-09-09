package dev.harpia.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A second place a diagnostic points at, as data rather than as text inside the message. */
class RelatedLocationTest {

    @Test
    void aDiagnosticCanPointAtAnotherPlaceWithoutSayingSoInItsMessage() {
        Diagnostic diagnostic = Diagnostic
                .error("HRP2001", "entity 'Customer' is declared more than once",
                        SourceRef.of("specs/a.harpia.md", 1, 1))
                .relatedTo("also declared here", SourceRef.of("specs/b.harpia.md", 1, 1));

        assertThat(diagnostic.message()).doesNotContain("specs/b.harpia.md");
        assertThat(diagnostic.related()).singleElement().satisfies(related -> {
            assertThat(related.message()).isEqualTo("also declared here");
            assertThat(related.where().file()).isEqualTo("specs/b.harpia.md");
        });
    }

    @Test
    void relatedLocationsOrderByPlaceThenMessage() {
        RelatedLocation later = new RelatedLocation("b", SourceRef.of("specs/z.harpia.md", 1, 1));
        RelatedLocation earlier = new RelatedLocation("a", SourceRef.of("specs/a.harpia.md", 9, 1));

        assertThat(List.of(later, earlier).stream().sorted().toList())
                .containsExactly(earlier, later);
    }

    @Test
    void twoDiagnosticsDifferingOnlyInRelatedLocationsStillOrderTotally() {
        Diagnostic first = Diagnostic
                .error("HRP2001", "same", SourceRef.of("specs/a.harpia.md", 1, 1))
                .relatedTo("here", SourceRef.of("specs/b.harpia.md", 1, 1));
        Diagnostic second = Diagnostic
                .error("HRP2001", "same", SourceRef.of("specs/a.harpia.md", 1, 1))
                .relatedTo("here", SourceRef.of("specs/c.harpia.md", 1, 1));

        assertThat(DiagnosticOrdering.sorted(List.of(second, first)))
                .containsExactly(first, second);
    }
}
