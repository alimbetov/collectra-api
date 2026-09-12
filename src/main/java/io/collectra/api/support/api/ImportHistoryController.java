package io.collectra.api.support.api;

import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.support.application.FrontendHistoryQueryService;
import io.collectra.api.support.application.FrontendHistoryQueryService.ImportErrorItem;
import io.collectra.api.support.application.FrontendHistoryQueryService.ImportItem;
import io.collectra.api.support.application.FrontendHistoryQueryService.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
@PreAuthorize("@importBatchAuthorization.canRead(authentication)")
public class ImportHistoryController {
    private final FrontendHistoryQueryService queries;

    public ImportHistoryController(FrontendHistoryQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<ImportItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.imports(tenant(), status, createdFrom, createdTo, page, size);
    }

    @GetMapping("/{importId}")
    public ImportItem get(@PathVariable UUID importId) {
        return queries.importDetail(tenant(), importId);
    }

    @GetMapping("/{importId}/errors")
    public PageResponse<ImportErrorItem> errors(
            @PathVariable UUID importId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.importErrors(tenant(), importId, page, size);
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }
}
