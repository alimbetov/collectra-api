package io.collectra.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Browser-only BFF operations. Access and refresh tokens never enter the response. */
@RestController
@RequestMapping("/api/v1/iam")
public class IamBrowserSessionController {
    private final OAuth2AuthorizedClientManager manager;
    private final ClientRegistrationRepository registrations;

    public IamBrowserSessionController(OAuth2AuthorizedClientManager manager,
            ClientRegistrationRepository registrations) {
        this.manager = manager;
        this.registrations = registrations;
    }

    @PostMapping("/session/refresh")
    public ResponseEntity<Map<String,Object>> refresh(Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof OidcUser)) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                    .body(Map.of("authenticated", false));
        }
        var authorize = OAuth2AuthorizeRequest.withClientRegistrationId(oauth.getAuthorizedClientRegistrationId())
                .principal(authentication)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();
        var client = manager.authorize(authorize);
        if (client == null || client.getAccessToken() == null)
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                    .body(Map.of("authenticated", false));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("authenticated", true));
    }

    @PostMapping("/logout/global")
    public void globalLogout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken))
            { response.sendError(401); return; }
        var handler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        handler.onLogoutSuccess(request, response, authentication);
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
