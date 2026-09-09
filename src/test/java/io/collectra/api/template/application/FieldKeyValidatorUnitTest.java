package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldKeyValidatorUnitTest {
    private final FieldKeyValidator validator = new FieldKeyValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "custom.invoice.total",
        "custom.invoice.manager_name",
        "custom.erp.customer.external_code",
        "custom.a.b"
    })
    void acceptsCanonicalCustomFieldKeys(String key) {
        assertThat(validator.validateCustomFieldKey(key).canonical()).isEqualTo(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "invoice.total",
        "custom.invoice",
        "Custom.Invoice.Total",
        "custom..total",
        "custom.invoice-total.value",
        "custom.invoice.1total",
        "custom.invoice.total.",
        ".custom.invoice.total",
        "custom invoice total",
        "custom"
    })
    void rejectsInvalidCustomFieldKeys(String key) {
        assertThatThrownBy(() -> validator.validateCustomFieldKey(key))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesOrdinaryPlaceholderKeysThroughSameGrammar() {
        FieldPath path = validator.validatePlaceholderKey("document.number");

        assertThat(path.canonical()).isEqualTo("document.number");
        assertThat(path.jsonPointer()).isEqualTo("/document/number");
    }

    @Test
    void doesNotNormalizeUppercaseSilently() {
        assertThatThrownBy(() -> validator.validateCustomFieldKey("CUSTOM.invoice.total"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical lowercase");
    }
}
