package io.collectra.api.integration.api;

import io.collectra.api.integration.application.ServiceClientService;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration")
public class ServiceClientController {
    private final ServiceClientService service;

    public ServiceClientController(ServiceClientService service) {
        this.service = service;
    }

    @PostMapping("/service-clients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_CREATE')")
    ServiceClientService.ClientResponse create(@Valid @RequestBody CreateRequest request) {
        return service.create(
                TenantContext.requireTenantId(),
                request.clientId(),
                request.name(),
                request.clientSecret(),
                request.scopes(),
                request.ipAllowlist(),
                request.expiresAt(),
                request.secretExpiresAt());
    }

    @GetMapping("/service-clients")
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_READ')")
    List<ServiceClientService.ClientResponse> list() {
        return service.list(TenantContext.requireTenantId());
    }

    @PostMapping("/service-clients/{id}/rotate-secret")
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_ROTATE_SECRET')")
    ServiceClientService.ClientResponse startRotation(
            @PathVariable UUID id, @Valid @RequestBody RotateSecretRequest request) {
        return service.startRotation(
                TenantContext.requireTenantId(),
                id,
                request.clientSecret(),
                request.secretExpiresAt());
    }

    @PostMapping("/service-clients/{id}/credentials/{credentialId}/activate")
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_ROTATE_SECRET')")
    ServiceClientService.ClientResponse completeRotation(
            @PathVariable UUID id, @PathVariable UUID credentialId) {
        return service.completeRotation(TenantContext.requireTenantId(), id, credentialId);
    }

    @PostMapping("/service-clients/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_BLOCK')")
    void block(@PathVariable UUID id) {
        service.block(TenantContext.requireTenantId(), id);
    }

    @PostMapping("/service-clients/{id}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_BLOCK')")
    void unblock(@PathVariable UUID id) {
        service.unblock(TenantContext.requireTenantId(), id);
    }

    @PostMapping("/service-token")
    ServiceClientService.TokenResponse token(
            @Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        return service.token(
                request.clientId(),
                request.clientSecret(),
                request.scopes(),
                http.getRemoteAddr());
    }

    record CreateRequest(
            @Pattern(regexp = "[a-z0-9-]{3,100}") String clientId,
            @NotBlank String name,
            @Size(min = 32, max = 72) String clientSecret,
            @NotEmpty Set<String> scopes,
            Set<String> ipAllowlist,
            Instant expiresAt,
            Instant secretExpiresAt) {
        CreateRequest {
            if (ipAllowlist == null) {
                ipAllowlist = Set.of();
            }
        }
    }

    record RotateSecretRequest(
            @Size(min = 32, max = 72) String clientSecret, Instant secretExpiresAt) {}

    record TokenRequest(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotEmpty Set<String> scopes) {}
}
