package io.collectra.api.identity.api;

import io.collectra.api.identity.application.UserLifecycleService;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/me")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CurrentUserController {
    private final UserLifecycleService lifecycle;

    public CurrentUserController(UserLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @GetMapping
    UserLifecycleService.MeResponse me(JwtAuthenticationToken authentication) {
        return lifecycle.me(userId(authentication), TenantContext.requireTenantId(), membershipId(authentication));
    }

    @PatchMapping
    UserLifecycleService.MeResponse update(
            JwtAuthenticationToken authentication, @Valid @RequestBody UpdateRequest request) {
        return lifecycle.updateMe(
                userId(authentication),
                TenantContext.requireTenantId(),
                membershipId(authentication),
                request.displayName(),
                request.locale(),
                request.timezone());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        lifecycle.changePassword(userId(authentication), request.currentPassword(), request.newPassword());
    }

    @GetMapping("/sessions")
    List<UserLifecycleService.SessionResponse> sessions(JwtAuthenticationToken authentication) {
        return lifecycle.sessions(userId(authentication), membershipId(authentication));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        lifecycle.revokeSession(userId(authentication), membershipId(authentication), id);
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    private UUID membershipId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getClaimAsString("membership_id"));
    }

    record UpdateRequest(
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 10) String locale,
            @Size(max = 60) String timezone) {}

    record ChangePasswordRequest(
            @NotBlank String currentPassword, @Size(min = 12, max = 72) String newPassword) {}
}
