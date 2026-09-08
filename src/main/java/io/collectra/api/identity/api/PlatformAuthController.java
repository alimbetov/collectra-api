package io.collectra.api.identity.api;

import io.collectra.api.identity.application.PlatformAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {
    private final PlatformAuthService auth;

    public PlatformAuthController(PlatformAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    PlatformAuthService.PlatformTokens login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    PlatformAuthService.PlatformTokens refresh(@Valid @RequestBody TokenRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody TokenRequest request) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
    ResponseEntity<Void> logoutAll(JwtAuthenticationToken authentication) {
        auth.logoutAll(UUID.fromString(authentication.getToken().getSubject()));
        return ResponseEntity.noContent().build();
    }

    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record TokenRequest(@NotBlank String refreshToken) {}
}
