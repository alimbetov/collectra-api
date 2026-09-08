package io.collectra.api.integration.api;

import io.collectra.api.integration.application.ServiceClientService;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integration")
public class ServiceClientController {
    private final ServiceClientService service;
    public ServiceClientController(ServiceClientService service) { this.service = service; }
    @PostMapping("/service-clients") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_CREATE')")
    ServiceClientService.SecretResponse create(@Valid @RequestBody CreateRequest request) {
        return service.create(TenantContext.requireTenantId(), request.clientId(), request.name(), request.scopes(), request.ipAllowlist());
    }
    @PostMapping("/service-clients/{id}/rotate-secret")
    @PreAuthorize("hasAuthority('SERVICE_CLIENT_ROTATE_SECRET')")
    ServiceClientService.SecretResponse rotate(@PathVariable UUID id) { return service.rotate(TenantContext.requireTenantId(), id); }
    @PostMapping("/service-token")
    ServiceClientService.TokenResponse token(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        return service.token(request.clientId(), request.clientSecret(), request.scopes(), http.getRemoteAddr());
    }
    record CreateRequest(@Pattern(regexp = "[a-z0-9-]{3,100}") String clientId, @NotBlank String name,
            @NotEmpty Set<String> scopes, Set<String> ipAllowlist) {
        CreateRequest { if (ipAllowlist == null) ipAllowlist = Set.of(); }
    }
    record TokenRequest(@NotBlank String clientId, @NotBlank String clientSecret, @NotEmpty Set<String> scopes) {}
}
