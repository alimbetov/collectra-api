package io.collectra.api.identity.api;

import io.collectra.api.identity.application.UserLifecycleService;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
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
@RequestMapping("/api/v1/identity")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class UserLifecycleController {
    private final UserLifecycleService lifecycle;

    public UserLifecycleController(UserLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @GetMapping("/me")
    UserLifecycleService.MeResponse me(JwtAuthenticationToken authentication) {
        return lifecycle.me(userId(authentication), TenantContext.requireTenantId(), membershipId(authentication));
    }

    @PatchMapping("/me")
    UserLifecycleService.MeResponse updateMe(
            JwtAuthenticationToken authentication, @Valid @RequestBody UpdateMeRequest request) {
        return lifecycle.updateMe(
                userId(authentication),
                TenantContext.requireTenantId(),
                membershipId(authentication),
                request.displayName(),
                request.locale(),
                request.timezone());
    }

    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        lifecycle.changePassword(userId(authentication), request.currentPassword(), request.newPassword());
    }

    @GetMapping("/me/sessions")
    List<UserLifecycleService.SessionResponse> sessions(JwtAuthenticationToken authentication) {
        return lifecycle.sessions(userId(authentication), membershipId(authentication));
    }

    @DeleteMapping("/me/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        lifecycle.revokeSession(userId(authentication), membershipId(authentication), id);
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_INVITE')")
    UserLifecycleService.InvitationResponse invite(
            JwtAuthenticationToken authentication, @Valid @RequestBody InviteRequest request) {
        return lifecycle.invite(
                TenantContext.requireTenantId(),
                userId(authentication),
                request.email(),
                request.roleIds());
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('USER_INVITE')")
    List<UserLifecycleService.InvitationResponse> invitations() {
        return lifecycle.invitations(TenantContext.requireTenantId());
    }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_INVITE')")
    void revokeInvitation(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        lifecycle.revokeInvitation(TenantContext.requireTenantId(), id, userId(authentication));
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    private UUID membershipId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getClaimAsString("membership_id"));
    }

    record UpdateMeRequest(
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 10) String locale,
            @Size(max = 60) String timezone) {}

    record ChangePasswordRequest(
            @NotBlank String currentPassword, @Size(min = 12, max = 72) String newPassword) {}

    record InviteRequest(@Email @NotBlank String email, Set<UUID> roleIds) {}
}
