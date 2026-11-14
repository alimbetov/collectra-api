package io.collectra.platform.revocation;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Service-account admin client: never log secrets, tokens, or user identifiers. */
@Component
public class KeycloakAdminClient {
    private final RestClient http = RestClient.create();
    private final String base, realm, clientId, secret;
    public KeycloakAdminClient(
        @Value("${app-common.keycloak.base-url:http://localhost:8180}") String base,
        @Value("${app-common.keycloak.realm:shared}") String realm,
        @Value("${app-common.keycloak.admin-client-id:}") String clientId,
        @Value("${app-common.keycloak.admin-client-secret:}") String secret) {
        this.base=base; this.realm=realm; this.clientId=clientId; this.secret=secret;
    }
    public void disableAndLogout(String issuer, String subject) {
        if (!issuer.equals(base + "/realms/" + realm))
            throw new IllegalArgumentException("Unexpected issuer");
        if (clientId.isBlank() || secret.isBlank())
            throw new IllegalStateException("Keycloak admin credentials not configured");
        // Keycloak user IDs are path components; restrict them to safe UUIDs.
        java.util.UUID.fromString(subject);
        @SuppressWarnings("unchecked")
        Map<String,Object> token = http.post()
            .uri(base + "/realms/" + realm + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=client_credentials&client_id=" +
                java.net.URLEncoder.encode(clientId,java.nio.charset.StandardCharsets.UTF_8) +
                "&client_secret=" + java.net.URLEncoder.encode(secret,java.nio.charset.StandardCharsets.UTF_8))
            .retrieve().body(Map.class);
        String bearer = (String) token.get("access_token");
        http.put().uri(base + "/admin/realms/" + realm + "/users/" + subject)
            .headers(h -> h.setBearerAuth(bearer)).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("enabled",false)).retrieve().toBodilessEntity();
        http.post().uri(base + "/admin/realms/" + realm + "/users/" + subject + "/logout")
            .headers(h -> h.setBearerAuth(bearer)).retrieve().toBodilessEntity();
    }
}
