package io.collectra.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigurationTest {
    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void prodProfileRequiresExternalDatabaseAndSecuritySecrets() throws IOException {
        PropertySource<?> prod = load("application-prod.yml");

        assertThat(prod.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(prod.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(prod.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(prod.getProperty("collectra.security.jwt-secret"))
                .isEqualTo("${COLLECTRA_JWT_SECRET}");
        assertThat(prod.getProperty("collectra.security.otp-pepper"))
                .isEqualTo("${COLLECTRA_OTP_PEPPER}");
        assertThat(prod.getProperty("collectra.file.storage.endpoint"))
                .isEqualTo("${RUSTFS_ENDPOINT}");
        assertThat(prod.getProperty("collectra.file.storage.access-key"))
                .isEqualTo("${RUSTFS_ACCESS_KEY}");
        assertThat(prod.getProperty("collectra.file.storage.secret-key"))
                .isEqualTo("${RUSTFS_SECRET_KEY}");
    }

    @Test
    void sharedConfigurationContainsNoDevelopmentCredentialFallbacks() throws IOException {
        PropertySource<?> shared = load("application.yml");

        assertThat(shared.getProperty("spring.datasource.password")).isNull();
        assertThat(shared.getProperty("collectra.security.jwt-secret")).isNull();
        assertThat(shared.getProperty("collectra.security.otp-pepper")).isNull();
        assertThat(shared.getProperty("collectra.file.storage.access-key")).isNull();
        assertThat(shared.getProperty("collectra.file.storage.secret-key")).isNull();
        assertThat(shared.getProperty("collectra.communication.delivery.enabled")).isEqualTo(false);
    }

    @Test
    void localProfileOwnsDevelopmentCredentials() throws IOException {
        PropertySource<?> local = load("application-local.yml");

        assertThat(local.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/app_db");
        assertThat(local.getProperty("collectra.security.jwt-secret")).isNotNull();
        assertThat(local.getProperty("collectra.security.otp-pepper")).isNotNull();
        assertThat(local.getProperty("collectra.file.storage.endpoint"))
                .isEqualTo("http://localhost:9000");
    }

    @Test
    void testProfileKeepsBackgroundWorkersDisabled() throws IOException {
        PropertySource<?> test = load("application-test.yml");

        assertThat(test.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo(3);
        assertThat(test.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo(0);
        assertThat(test.getProperty("spring.rabbitmq.listener.simple.auto-startup"))
                .isEqualTo(false);
        assertThat(test.getProperty("spring.task.scheduling.enabled")).isEqualTo(false);
        assertThat(test.getProperty("collectra.file.cleanup.enabled")).isEqualTo(false);
        assertThat(test.getProperty("collectra.messaging.outbox-enabled")).isEqualTo(false);
    }

    private PropertySource<?> load(String resource) throws IOException {
        return loader.load(resource, new ClassPathResource(resource)).get(0);
    }
}
