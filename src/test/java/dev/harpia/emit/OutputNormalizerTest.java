package dev.harpia.emit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputNormalizerTest {

    @Test
    void removesBomPlatformNewlinesTrailingSpaceAndRepeatedBlankLines() {
        String input = "\uFEFFfirst  \r\n\r\n  \r\nsecond\t\rthird\r\n\r\n";

        String normalized = OutputNormalizer.normalize(input);

        assertThat(normalized).isEqualTo("first\n\nsecond\nthird\n");
    }

    @Test
    void normalizationIsIdempotent() {
        String once = OutputNormalizer.normalize("one\n\n\n two   \n");

        assertThat(OutputNormalizer.normalize(once)).isEqualTo(once);
        assertThat(once).endsWith("\n").doesNotEndWith("\n\n");
    }
}
