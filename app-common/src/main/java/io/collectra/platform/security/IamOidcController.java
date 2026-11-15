package io.collectra.platform.security;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @GetMapping("/session")
    public ResponseEntity<Map<String,Object>> session(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "authenticated", true,
                "subject", jwt.getSubject(),
                "issuer", jwt.getIssuer().toString(),
                "audience", jwt.getAudience()));
    }
}
