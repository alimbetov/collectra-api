package io.collectra.api.identity.api;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * IAM-R3.1: hosted Keycloak registration and email verification entry points.
 * Never accepts passwords or verification action tokens in Collectra.
 * User activation and local UUID linking require separate verified onboarding.
 */
@RestController
@RequestMapping("/api/v1/auth/keycloak/registration")
public class KeycloakRegistrationController {
    private final URI registrationUri;
    private final boolean enabled;

    public KeycloakRegistrationController(
            @Value("${collectra.security.keycloak.registration-url:}") String registrationUrl,
            @Value("${collectra.security.keycloak.registration-enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.registrationUri = registrationUrl.isBlank() ? null : URI.create(registrationUrl);
        if (enabled && (registrationUri == null || !"https".equalsIgnoreCase(registrationUri.getScheme()))) {
            throw new IllegalStateException("Hosted Keycloak registration requires an HTTPS URL");
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> registration() {
        if (!enabled) return ResponseEntity.status(503).body(Map.of("status", "NOT_CONFIGURED"));
        return ResponseEntity.ok(Map.of(
                "status", "KEYCLOAK_HOSTED",
                "registrationUrl", registrationUri.toASCIIString(),
                "emailVerificationRequired", true));
    }

    @GetMapping("/verification")
    public ResponseEntity<Map<String, String>> verification() {
        return ResponseEntity.ok(Map.of(
                "status", "KEYCLOAK_MANAGED",
                "message", "Email verification is completed by Keycloak; no Collectra verification token is accepted"));
    }
}
