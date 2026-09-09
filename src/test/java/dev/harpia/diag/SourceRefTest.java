package dev.harpia.diag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** A reference may know only where something starts, or also where it ends. */
class SourceRefTest {

    @Test
    void aPointHasNoEnd() {
        SourceRef point = SourceRef.of("specs/a.harpia.md", 3, 5);

        assertThat(point.end()).isEmpty();
        assertThat(point.describe()).isEqualTo("specs/a.harpia.md:3:5");
    }

    @Test
    void aSpanKnowsWhereItEnds() {
        SourceRef span = SourceRef.span("specs/a.harpia.md", 3, 5, 3, 12);

        assertThat(span.end()).contains(new SourceRef.Position(3, 12));
        assertThat(span.describe()).isEqualTo("specs/a.harpia.md:3:5-3:12");
        assertThat(span.start().end()).isEmpty();
    }

    @Test
    void anEndMustNotPrecedeItsStart() {
        assertThatThrownBy(() -> SourceRef.span("specs/a.harpia.md", 3, 5, 3, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not precede");
        assertThatThrownBy(() -> SourceRef.span("specs/a.harpia.md", 3, 5, 2, 9))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWholeFileReferenceCannotHaveAnEnd() {
        assertThatThrownBy(() -> SourceRef.file("harpia.yaml").endingAt(1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole-file");
        assertThat(SourceRef.file("harpia.yaml").describe()).isEqualTo("harpia.yaml");
    }

    @Test
    void aStartCanLearnItsEndLater() {
        assertThat(SourceRef.of("specs/a.harpia.md", 1, 1).endingAt(1, 9))
                .isEqualTo(SourceRef.span("specs/a.harpia.md", 1, 1, 1, 9));
    }
}
