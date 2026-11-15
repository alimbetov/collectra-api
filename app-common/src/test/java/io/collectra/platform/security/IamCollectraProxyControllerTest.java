package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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

    @Test void administrativeAndMutationRoutesAreDeniedBeforeTokenLookup() {
        var controller = new IamCollectraProxyController(manager, "http://localhost:8080");
        var request = mock(HttpServletRequest.class);
        var principal = mock(OidcUser.class);
        var auth = new OAuth2AuthenticationToken(principal, java.util.List.of(), "collectra");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/iam/collectra/api/v1/platform/iam/onboarding");
        assertThat(controller.forward(request, null, auth, null).getStatusCode().value()).isEqualTo(403);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/iam/collectra/api/v1/identity/me");
        assertThat(controller.forward(request, null, auth, null).getStatusCode().value()).isEqualTo(403);
    }

    @Test void unauthenticatedRequestsNeverReachUpstream() {
        var controller = new IamCollectraProxyController(manager, "http://localhost:8080");
        var response = controller.forward(mock(HttpServletRequest.class), null, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
