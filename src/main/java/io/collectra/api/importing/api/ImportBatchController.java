package io.collectra.api.importing.api;

import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.importing.application.ImportBatchService;
import io.collectra.api.shared.tenant.TenantContext;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/import-batches")
public class ImportBatchController {
    private final ImportBatchService service;
    public ImportBatchController(ImportBatchService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@importBatchAuthorization.canCreate(authentication)")
    ImportBatchService.BatchResult create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam UUID mappingProfileVersionId,
            @RequestParam UUID templateVersionId,
            @RequestParam(defaultValue = "HTML,PDF") Set<OutputFormat> formats,
            @RequestPart("file") MultipartFile file) throws IOException {
        return service.create(TenantContext.requireTenantId(), idempotencyKey,
                mappingProfileVersionId, templateVersionId, file.getBytes(), formats);
    }

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@importBatchAuthorization.canCreate(authentication)")
    ImportBatchService.BatchResult createJson(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam UUID mappingProfileVersionId,
            @RequestParam UUID templateVersionId,
            @RequestParam(defaultValue = "HTML,PDF") Set<OutputFormat> formats,
            @RequestBody byte[] body) {
        return service.create(TenantContext.requireTenantId(), idempotencyKey,
                mappingProfileVersionId, templateVersionId, body, formats);
    }

    @PostMapping(value = "/xml", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@importBatchAuthorization.canCreate(authentication)")
    ImportBatchService.BatchResult createXml(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam UUID mappingProfileVersionId,
            @RequestParam UUID templateVersionId,
            @RequestParam(defaultValue = "HTML,PDF") Set<OutputFormat> formats,
            @RequestBody byte[] body) {
        return service.create(TenantContext.requireTenantId(), idempotencyKey,
                mappingProfileVersionId, templateVersionId, body, formats);
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("@importBatchAuthorization.canRead(authentication)")
    ImportBatchService.BatchResult get(@PathVariable UUID batchId) {
        return service.get(TenantContext.requireTenantId(), batchId);
    }
}
