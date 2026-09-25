package io.collectra.api.integration.api;

import io.collectra.api.integration.application.IngestionApplicationService;
import io.collectra.api.shared.tenant.TenantContext;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integration/sources")
public class IngestionController {
    private final IngestionApplicationService service;
    public IngestionController(IngestionApplicationService service) { this.service = service; }

    @PostMapping(value="/{sourceCode}/ingestions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('ROLE_SERVICE') and hasAuthority('SCOPE_integration:imports:create')")
    public IngestionApplicationService.Reservation ingest(@PathVariable String sourceCode,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value="X-Request-Id", required=false) String requestId,
            @RequestHeader(value=HttpHeaders.CONTENT_TYPE, defaultValue=MediaType.APPLICATION_JSON_VALUE) String contentType,
            @RequestBody byte[] body, @AuthenticationPrincipal Jwt jwt) {
        return service.reserve(TenantContext.requireTenantId(), UUID.fromString(jwt.getSubject()),
                sourceCode, key, requestId, contentType, body);
    }

    @GetMapping("/{sourceCode}/ingestions/{id}")
    @PreAuthorize("hasAuthority('ROLE_SERVICE') and hasAuthority('SCOPE_integration:imports:read')")
    public IngestionApplicationService.Reservation status(@PathVariable String sourceCode,
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        var result = service.status(TenantContext.requireTenantId(), id);
        if (!result.sourceCode().equals(sourceCode)) throw new java.util.NoSuchElementException("Ingestion batch not found");
        return result;
    }
}
