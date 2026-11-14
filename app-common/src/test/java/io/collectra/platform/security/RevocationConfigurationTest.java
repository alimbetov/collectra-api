package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class RevocationConfigurationTest {
    @Test void keycloakAndRevocationConfigurationAreUnderAppCommon() throws Exception {
        var properties = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml")).get(0);
        assertThat(properties.getProperty("app-common.keycloak.base-url")).isNotNull();
        assertThat(properties.getProperty("app-common.revocation.dispatch-enabled")).isNotNull();
        assertThat(properties.getProperty("app-common.revocation.reconcile-enabled")).isNotNull();
        assertThat(properties.getProperty("management.keycloak.base-url")).isNull();
    }
}
