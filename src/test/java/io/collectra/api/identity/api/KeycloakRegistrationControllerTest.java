package io.collectra.api.identity.api;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KeycloakRegistrationControllerTest {
    @Test
    void disabledRegistrationFailsClosed() {
        var response = new KeycloakRegistrationController("", false).registration();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void enabledRegistrationRequiresHttps() {
        assertThatThrownBy(
                        () ->
                                new KeycloakRegistrationController(
                                        "http://localhost:8180/realms/shared/protocol/openid-connect/registrations",
                                        true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enabledRegistrationReturnsHostedUrlWithoutLocalCredentials() {
        String url = "https://iam.example.test/realms/shared/protocol/openid-connect/registrations";
        var response = new KeycloakRegistrationController(url, true).registration();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("registrationUrl", url)
                .containsEntry("emailVerificationRequired", true);
    }

    @Test
    void verificationIsKeycloakManaged() {
        var response = new KeycloakRegistrationController("", false).verification();
        assertThat(response.getBody()).containsEntry("status", "KEYCLOAK_MANAGED");
    }
}
