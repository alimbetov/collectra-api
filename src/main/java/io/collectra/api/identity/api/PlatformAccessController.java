package io.collectra.api.identity.api;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stable entry point for platform administration APIs. */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
public class PlatformAccessController {

    @GetMapping("/me")
    PlatformPrincipalResponse me(JwtAuthenticationToken authentication) {
        return new PlatformPrincipalResponse(UUID.fromString(authentication.getToken().getSubject()));
    }

    record PlatformPrincipalResponse(UUID userId) {}
}
