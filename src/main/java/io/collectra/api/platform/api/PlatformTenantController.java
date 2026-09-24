package io.collectra.api.platform.api;

import io.collectra.api.platform.application.PlatformTenantLifecycleService;
import io.collectra.api.platform.application.PlatformTenantQueryService;
import io.collectra.api.platform.application.PlatformTenantQueryService.PageResponse;
import io.collectra.api.platform.application.PlatformTenantQueryService.TenantDetail;
import io.collectra.api.platform.application.PlatformTenantQueryService.TenantItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
public class PlatformTenantController {
    private final PlatformTenantQueryService queries;
    private final PlatformTenantLifecycleService lifecycle;

    public PlatformTenantController(
            PlatformTenantQueryService queries, PlatformTenantLifecycleService lifecycle) {
        this.queries = queries;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    PageResponse<TenantItem> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.tenants(search, status, createdFrom, createdTo, page, size, sort);
    }

    @GetMapping("/{tenantId}")
    TenantDetail detail(@PathVariable UUID tenantId) {
        return queries.tenant(tenantId);
    }

    @PatchMapping("/{tenantId}/status")
    TenantDetail changeStatus(
            JwtAuthenticationToken authentication,
            @PathVariable UUID tenantId,
            @Valid @RequestBody PlatformTenantStatusRequest request) {
        lifecycle.changeStatus(
                actorId(authentication),
                tenantId,
                request.active(),
                request.revision(),
                request.reason());
        return queries.tenant(tenantId);
    }

    private UUID actorId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }

    record PlatformTenantStatusRequest(
            @NotNull Boolean active,
            @NotNull @Min(0) Long revision,
            @NotBlank @Size(max = 255) String reason) {}
}
