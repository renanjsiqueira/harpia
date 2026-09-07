package dev.harpia.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class NamingTest {

    @Test
    void canonicalNamesDoNotDependOnTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            assertThat(Naming.useCaseBaseName("Create Invoice Item"))
                    .isEqualTo("CreateInvoiceItem");
        } finally {
            Locale.setDefault(original);
        }
    }
}
