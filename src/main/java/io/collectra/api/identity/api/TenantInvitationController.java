package io.collectra.api.identity.api;

import io.collectra.api.identity.application.UserLifecycleService;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/invitations")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('USER_INVITE')")
public class TenantInvitationController {
    private final UserLifecycleService lifecycle;

    public TenantInvitationController(UserLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserLifecycleService.InvitationResponse invite(
            JwtAuthenticationToken authentication, @Valid @RequestBody InviteRequest request) {
        return lifecycle.invite(
                TenantContext.requireTenantId(),
                userId(authentication),
                request.email(),
                request.roleIds());
    }

    @GetMapping
    List<UserLifecycleService.InvitationResponse> list() {
        return lifecycle.invitations(TenantContext.requireTenantId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        lifecycle.revokeInvitation(TenantContext.requireTenantId(), id, userId(authentication));
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    record InviteRequest(@Email @NotBlank String email, Set<UUID> roleIds) {}
}
