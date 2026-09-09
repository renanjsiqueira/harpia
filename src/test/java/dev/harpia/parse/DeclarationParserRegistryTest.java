package dev.harpia.parse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.LanguageVersion;
import org.junit.jupiter.api.Test;

class DeclarationParserRegistryTest {

    private final DeclarationParserRegistry registry =
            DeclarationParserRegistry.forLanguageVersion(LanguageVersion.V0);

    @Test
    void routesEveryCurrentDeclarationByStableKind() {
        assertThat(registry.parserFor("Data").kind()).isEqualTo(DeclarationKind.ENTITY);
        assertThat(registry.parserFor("Logic CalculateTotal").kind())
                .isEqualTo(DeclarationKind.LOGIC);
        assertThat(registry.parserFor("Scenario VIP discount").kind())
                .isEqualTo(DeclarationKind.SCENARIO);
        assertThat(registry.parserFor("Create Customer").kind())
                .isEqualTo(DeclarationKind.USE_CASE);
    }

    @Test
    void malformedPrefixedDeclarationsStillReachTheirOwnParser() {
        assertThat(registry.parserFor("Logic").kind()).isEqualTo(DeclarationKind.LOGIC);
        assertThat(registry.parserFor("Scenario").kind()).isEqualTo(DeclarationKind.SCENARIO);
    }

    @Test
    void registryOrderDoesNotDependOnHashIteration() {
        assertThat(registry.kinds())
                .containsExactly(
                        DeclarationKind.ENTITY,
                        DeclarationKind.LOGIC,
                        DeclarationKind.SCENARIO,
                        DeclarationKind.USE_CASE);
        assertThat(DeclarationParserRegistry.forLanguageVersion(LanguageVersion.V0).kinds())
                .isEqualTo(registry.kinds());
    }
}
