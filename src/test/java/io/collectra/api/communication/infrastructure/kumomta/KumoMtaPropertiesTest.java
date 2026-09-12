package io.collectra.api.communication.infrastructure.kumomta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class KumoMtaPropertiesTest {
    @Test
    void acceptsIpBasedHttpBaseUrl() {
        KumoMtaProperties properties = valid();

        properties.validate();

        assertThat(properties.effectiveFromHeader()).isEqualTo("sender@collectra.kz");
    }

    @Test
    void failsFastForMissingOrUnsafeConfiguration() {
        KumoMtaProperties properties = valid();
        properties.setBaseUrl("");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base-url");

        properties = valid();
        properties.setConnectTimeout(Duration.ZERO);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connect-timeout");

        properties = valid();
        properties.setUsername("user");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured together");
    }

    private KumoMtaProperties valid() {
        KumoMtaProperties properties = new KumoMtaProperties();
        properties.setBaseUrl("http://10.10.10.20:8000");
        properties.setEnvelopeSender("sender@collectra.kz");
        return properties;
    }
}
