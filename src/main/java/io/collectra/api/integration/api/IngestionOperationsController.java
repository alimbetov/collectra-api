package io.collectra.api.integration.api;

import io.collectra.api.integration.application.IngestionQueryService;
import io.collectra.api.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integration/ingestion-batches")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_READ')")
public class IngestionOperationsController {
    private final IngestionQueryService service;
    public IngestionOperationsController(IngestionQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Page<IngestionQueryService.BatchSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) String idempotencyKey,
            @PageableDefault(size = 50, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(TenantContext.requireTenantId(), status, from, to, sourceCode, idempotencyKey, pageable);
    }
    @GetMapping("/{id}")
    public IngestionQueryService.BatchDetail get(@PathVariable UUID id) {
        return service.get(TenantContext.requireTenantId(), id);
    }
    @GetMapping("/{id}/records")
    public Page<IngestionQueryService.RecordDiagnostic> records(@PathVariable UUID id,
            @RequestParam(required = false) String outcome,@RequestParam(required = false) String targetType,
            @PageableDefault(size = 50, sort = "recordOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.records(TenantContext.requireTenantId(), id, outcome, targetType, pageable);
    }
}
