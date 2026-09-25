package io.collectra.api.importing.api;

import io.collectra.api.importing.application.ImportOperationsQueryService;
import io.collectra.api.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/import-batches")
@PreAuthorize("@importBatchAuthorization.canRead(authentication)")
public class ImportOperationsController {
    private final ImportOperationsQueryService service;

    public ImportOperationsController(ImportOperationsQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ImportOperationsQueryService.BatchSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) String idempotencyKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) List<String> sort) {
        return service.list(
                TenantContext.requireTenantId(),
                status,
                from,
                to,
                idempotencyKey,
                page,
                size,
                sort);
    }

    @GetMapping("/{id}/operations")
    public ImportOperationsQueryService.BatchDetail detail(@PathVariable UUID id) {
        return service.get(TenantContext.requireTenantId(), id);
    }
}
