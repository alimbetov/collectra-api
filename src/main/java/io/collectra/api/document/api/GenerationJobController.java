package io.collectra.api.document.api;

import io.collectra.api.document.application.GeneratedOutputService;
import io.collectra.api.document.application.GenerationJobService;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.shared.tenant.TenantContext;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents/generation-jobs")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class GenerationJobController {
    private final GenerationJobService jobs;
    private final GeneratedOutputService outputs;

    public GenerationJobController(GenerationJobService jobs, GeneratedOutputService outputs) {
        this.jobs = jobs;
        this.outputs = outputs;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('DOCUMENT_GENERATE')")
    JobResponse create(
            @RequestParam UUID mappingProfileId,
            @RequestParam UUID templateVersionId,
            @RequestParam(defaultValue = "HTML,PDF") Set<OutputFormat> formats,
            @RequestPart("file") MultipartFile file)
            throws java.io.IOException {
        return response(
                jobs.create(
                        TenantContext.requireTenantId(),
                        mappingProfileId,
                        templateVersionId,
                        file.getBytes(),
                        formats));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    JobResponse get(@PathVariable UUID jobId) {
        return response(jobs.get(TenantContext.requireTenantId(), jobId));
    }

    @GetMapping("/{jobId}/outputs")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    List<OutputResponse> outputs(@PathVariable UUID jobId) {
        return jobs.outputs(TenantContext.requireTenantId(), jobId).stream()
                .map(OutputResponse::from)
                .toList();
    }

    @GetMapping("/{jobId}/outputs/{format}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    ResponseEntity<byte[]> download(@PathVariable UUID jobId, @PathVariable OutputFormat format) {
        GeneratedDocument document = jobs.output(TenantContext.requireTenantId(), jobId, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getMediaType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=document."
                                + format.name().toLowerCase(java.util.Locale.ROOT))
                .body(outputs.read(document));
    }

    private JobResponse response(GenerationJob job) {
        return new JobResponse(
                job.getId(),
                job.getStatus().name(),
                job.getCurrentStep(),
                job.getAttemptCount(),
                job.getErrorCode(),
                job.getErrorMessage());
    }

    record JobResponse(
            UUID id,
            String status,
            String currentStep,
            int attemptCount,
            String errorCode,
            String errorMessage) {}

    record OutputResponse(OutputFormat format, String mediaType, long sizeBytes, String sha256) {
        static OutputResponse from(GeneratedDocument value) {
            return new OutputResponse(
                    value.getFormat(),
                    value.getMediaType(),
                    value.getSizeBytes(),
                    value.getSha256());
        }
    }
}
