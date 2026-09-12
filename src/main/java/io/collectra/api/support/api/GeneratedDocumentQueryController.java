package io.collectra.api.support.api;

import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.support.application.FrontendHistoryQueryService;
import io.collectra.api.support.application.FrontendHistoryQueryService.GeneratedDocumentItem;
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
@RequestMapping("/api/v1/generated-documents")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('DOCUMENT_READ')")
public class GeneratedDocumentQueryController {
    private final FrontendHistoryQueryService queries;

    public GeneratedDocumentQueryController(FrontendHistoryQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<GeneratedDocumentItem> list(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) UUID generationJobId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.generatedDocuments(
                tenant(), format, generationJobId, createdFrom, createdTo, page, size);
    }

    @GetMapping("/{documentId}")
    public GeneratedDocumentItem get(@PathVariable UUID documentId) {
        return queries.generatedDocument(tenant(), documentId);
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }
}
