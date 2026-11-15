package io.collectra.platform.security;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.view.RedirectView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM REST contract for non-browser clients. The actual OIDC authorization,
 * code exchange, refresh and logout are performed by Keycloak, not by this API.
 */
@RestController
@RequestMapping("/api/v1/iam")
public class IamOidcController {
    private final URI issuer;
    private final String clientId;

    public IamOidcController(
            @Value("${app-common.security.issuer-uri}") String issuer,
            @Value("${app-common.oidc.public-client-id:collectra-web}") String clientId) {
        this.issuer = URI.create(issuer);
        if (!"https".equalsIgnoreCase(this.issuer.getScheme())
                && !("http".equalsIgnoreCase(this.issuer.getScheme())
                     && "localhost".equalsIgnoreCase(this.issuer.getHost())))
            throw new IllegalArgumentException("OIDC issuer must use HTTPS (localhost HTTP allowed for development)");
        this.clientId = clientId;
    }

    @GetMapping("/oidc/config")
    public ResponseEntity<Map<String,Object>> config() {
        // Metadata only. Clients must fetch and validate the issuer's OIDC discovery document.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "issuer", issuer.toASCIIString(),
                "discoveryUrl", issuer.resolve(issuer.getPath().replaceAll("/+$", "") + "/.well-known/openid-configuration").toASCIIString(),
                "clientId", clientId,
                "responseType", "code",
                "pkceMethod", "S256"));
    }

    @GetMapping("/login")
    public RedirectView login() {
        return new RedirectView("/oauth2/authorization/collectra");
    }

    // /api/v1/iam/callback is consumed by Spring Security OAuth2LoginAuthenticationFilter.
    // Its state/nonce/PKCE checks must not be reimplemented in a controller.

    @GetMapping("/session")
    public ResponseEntity<Map<String,Object>> session(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof OidcUser oidc))
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                    .body(Map.of("authenticated", false));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "authenticated", true,
                "subject", oidc.getSubject(),
                "issuer", oidc.getIdToken().getIssuer().toString()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var cookie = new jakarta.servlet.http.Cookie("JSESSIONID", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
