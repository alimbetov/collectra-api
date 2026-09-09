package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceholderGrammarUnitTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "customer.name",
                "document.number",
                "custom.erp.code",
                "custom.invoice.manager_name",
                "custom.sap.customer.external_code"
            })
    void acceptsCanonicalNestedPaths(String key) {
        FieldPath path = PlaceholderGrammar.parse("  " + key + "  ");

        assertThat(path.canonical()).isEqualTo(key);
        assertThat(path.jsonPointer()).isEqualTo("/" + key.replace('.', '/'));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "customer",
                "Customer.Name",
                "customer..name",
                ".customer.name",
                "customer.name.",
                "customer-name.value",
                "customer.1name",
                "customer name.value",
                ""
            })
    void rejectsNonCanonicalOrMalformedPaths(String key) {
        assertThatThrownBy(() -> PlaceholderGrammar.parse(key))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
