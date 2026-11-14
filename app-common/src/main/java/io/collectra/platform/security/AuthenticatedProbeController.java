package io.collectra.platform.security;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthenticatedProbeController {
    @GetMapping("/api/v1/security/probe")
    Map<String, Object> probe(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("subject", jwt.getSubject(), "audience", jwt.getAudience());
    }
}
