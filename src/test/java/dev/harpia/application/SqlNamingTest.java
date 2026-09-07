package dev.harpia.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class SqlNamingTest {

    @Test
    void relationalNamesDoNotDependOnTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            assertThat(SqlNaming.identifier("InvoiceItem")).isEqualTo("invoice_item");
            assertThat(SqlNaming.identifier("URLValue2")).isEqualTo("url_value2");
            assertThat(SqlNaming.identifier("createdAt")).isEqualTo("created_at");
        } finally {
            Locale.setDefault(original);
        }
    }
}
