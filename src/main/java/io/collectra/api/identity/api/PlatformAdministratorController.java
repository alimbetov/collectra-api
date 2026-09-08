package io.collectra.api.identity.api;

import io.collectra.api.identity.application.PlatformAdministratorService;
import io.collectra.api.identity.domain.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/administrators")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
public class PlatformAdministratorController {
    private final PlatformAdministratorService administrators;

    public PlatformAdministratorController(PlatformAdministratorService administrators) {
        this.administrators = administrators;
    }

    @GetMapping
    List<AdministratorResponse> list() {
        return administrators.administrators().stream().map(AdministratorResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdministratorResponse create(@Valid @RequestBody CreateRequest request) {
        return AdministratorResponse.from(administrators.create(request.email(), request.password()));
    }

    @PatchMapping("/{id}/status")
    AdministratorResponse changeStatus(JwtAuthenticationToken authentication, @PathVariable UUID id,
            @RequestBody StatusRequest request) {
        return AdministratorResponse.from(administrators.changeStatus(
                actorId(authentication), id, request.active()));
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@PathVariable UUID id, @Valid @RequestBody PasswordRequest request) {
        administrators.changePassword(id, request.password());
    }

    @DeleteMapping("/{id}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeRole(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        administrators.removeRole(actorId(authentication), id);
    }

    private UUID actorId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    record CreateRequest(@Email @NotBlank String email,
            @NotBlank @Size(min = 12, max = 72) String password) {}
    record StatusRequest(boolean active) {}
    record PasswordRequest(@NotBlank @Size(min = 12, max = 72) String password) {}
    record AdministratorResponse(UUID id, String email, String status, long authorizationVersion,
            Instant createdAt) {
        static AdministratorResponse from(UserAccount user) {
            return new AdministratorResponse(user.getId(), user.getEmail(), user.getStatus(),
                    user.getAuthorizationVersion(), user.getCreatedAt());
        }
    }
}
