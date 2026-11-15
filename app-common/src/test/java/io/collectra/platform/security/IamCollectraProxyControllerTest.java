package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

class IamCollectraProxyControllerTest {
    private final OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);

    @Test void rejectsNonlocalPlaintextUpstream() {
        assertThrows(IllegalArgumentException.class,
                () -> new IamCollectraProxyController(manager, "http://external.example"));
    }

    @Test void disabledByDefault() {
        var condition = IamCollectraProxyController.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.name()).contains("app-common.bff.collectra-proxy-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test void unauthenticatedRequestsNeverReachUpstream() {
        var controller = new IamCollectraProxyController(manager, "http://localhost:8080");
        var response = controller.forward(mock(HttpServletRequest.class), null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
