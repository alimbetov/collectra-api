package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryErrorSummaryTest {
    private final DeliveryErrorSummary errors = new DeliveryErrorSummary();

    @Test
    void normalizesUnboundedProviderCodesToBoundedValues() {
        assertThat(errors.normalizeCode("socket_timeout_after_10s"))
                .isEqualTo("CONNECTION_TIMEOUT");
        assertThat(errors.normalizeCode("HTTP_429_RATE_LIMIT")).isEqualTo("RATE_LIMITED");
        assertThat(errors.normalizeCode("550 permanent reject"))
                .isEqualTo("PERMANENT_PROVIDER_REJECTION");
        assertThat(errors.normalizeCode("provider-specific-value-123456")).isEqualTo("UNKNOWN");
    }

    @Test
    void summaryNeverReturnsRawProviderMessage() {
        assertThat(errors.summary("UNKNOWN")).isEqualTo("Delivery failed");
    }
}
