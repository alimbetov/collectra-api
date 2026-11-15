package io.collectra.api.identity.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Admin API only. Keycloak owns passwords, verification links and SMTP delivery. */
@Component
public class KeycloakOnboardingClient {
    // Bound external IdP calls: an unavailable Keycloak must not hang onboarding workers.
    private final RestClient http = newHttpClient();

    private static RestClient newHttpClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory).build();
    }
    private final String base, realm, clientId, clientSecret;

    public KeycloakOnboardingClient(
            @Value("${collectra.security.keycloak.admin-base-url:http://localhost:8180}")
                    String base,
            @Value("${collectra.security.keycloak.admin-realm:shared}") String realm,
            @Value("${collectra.security.keycloak.admin-client-id:}") String clientId,
            @Value("${collectra.security.keycloak.admin-client-secret:}") String clientSecret) {
        this.base = base.replaceAll("/+$", "");
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private String token() {
        if (clientId.isBlank() || clientSecret.isBlank())
            throw new IllegalStateException("Keycloak admin client is not configured");
        @SuppressWarnings("unchecked")
        Map<String, Object> response =
                http.post()
                        .uri(base + "/realms/" + realm + "/protocol/openid-connect/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(
                                "grant_type=client_credentials&client_id="
                                        + java.net.URLEncoder.encode(
                                                clientId, StandardCharsets.UTF_8)
                                        + "&client_secret="
                                        + java.net.URLEncoder.encode(
                                                clientSecret, StandardCharsets.UTF_8))
                        .retrieve()
                        .body(Map.class);
        if (response == null || !(response.get("access_token") instanceof String value))
            throw new IllegalStateException("Missing Keycloak admin token");
        return value;
    }

    public void createUnverified(UUID subject, String email) {
        String bearer = token();
        var response =
                http.post()
                        .uri(base + "/admin/realms/" + realm + "/users")
                        .headers(h -> h.setBearerAuth(bearer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                Map.of(
                                        "id",
                                        subject.toString(),
                                        "username",
                                        email,
                                        "email",
                                        email,
                                        "enabled",
                                        true,
                                        "emailVerified",
                                        false,
                                        "requiredActions",
                                        List.of("VERIFY_EMAIL", "UPDATE_PASSWORD")))
                        .retrieve()
                        .toBodilessEntity();
        URI location = response.getHeaders().getLocation();
        if (location == null
                || !subject.toString()
                        .equals(
                                location.getPath()
                                        .substring(location.getPath().lastIndexOf('/') + 1)))
            throw new IllegalStateException("Keycloak returned an unexpected subject");
    }

    public void sendVerifyEmail(UUID subject) {
        http.put()
                .uri(base + "/admin/realms/" + realm + "/users/" + subject + "/send-verify-email")
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .toBodilessEntity();
    }

    public boolean matchesReservedIdentity(UUID subject, String expectedEmail) {
        Map<String, Object> user = readUser(subject);
        return user != null
                && subject.toString().equals(String.valueOf(user.get("id")))
                && expectedEmail.equalsIgnoreCase(String.valueOf(user.get("email")));
    }

    public boolean verified(UUID subject, String expectedEmail) {
        Map<String, Object> user = readUser(subject);
        return user != null
                && Boolean.TRUE.equals(user.get("enabled"))
                && Boolean.TRUE.equals(user.get("emailVerified"))
                && expectedEmail.equalsIgnoreCase(String.valueOf(user.get("email")));
    }

    private Map<String, Object> readUser(UUID subject) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user =
                http.get()
                        .uri(base + "/admin/realms/" + realm + "/users/" + subject)
                        .headers(h -> h.setBearerAuth(token()))
                        .retrieve()
                        .body(Map.class);
        return user;
    }
}
