package io.collectra.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class IamBrowserSessionControllerTest {
    @Test void exposesRefreshAndGlobalLogoutAsPostOnly() throws Exception {
        assertThat(IamBrowserSessionController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/iam");
        Method refresh = IamBrowserSessionController.class.getMethod("refresh",
                org.springframework.security.core.Authentication.class,
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        assertThat(refresh.getAnnotation(PostMapping.class).value())
                .containsExactly("/session/refresh");
        Method logout = IamBrowserSessionController.class.getMethod("globalLogout",
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class,
                org.springframework.security.core.Authentication.class);
        assertThat(logout.getAnnotation(PostMapping.class).value())
                .containsExactly("/logout/global");
    }

    @Test void refreshRejectsUnauthenticatedCallWithoutReturningTokens() {
        var controller = new IamBrowserSessionController(
                mock(OAuth2AuthorizedClientManager.class), mock(ClientRegistrationRepository.class));
        var result = controller.refresh(null, null, null);
        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(result.getBody()).containsEntry("authenticated", false)
                .doesNotContainKeys("accessToken", "refreshToken");
    }
}
