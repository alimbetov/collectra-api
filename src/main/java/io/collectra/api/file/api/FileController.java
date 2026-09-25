package io.collectra.api.file.api;

import io.collectra.api.file.application.FileDownload;
import io.collectra.api.file.application.FileMetadata;
import io.collectra.api.file.application.FileRegistryQueryService;
import io.collectra.api.file.application.FileService;
import io.collectra.api.file.application.PresignedDownload;
import io.collectra.api.file.application.UploadFileCommand;
import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.shared.tenant.TenantContext;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService fileService;
    private final FileRegistryQueryService registry;

    public FileController(FileService fileService, FileRegistryQueryService registry) {
        this.fileService = fileService;
        this.registry = registry;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@fileAuthorization.canUpload(authentication)")
    public FileMetadata upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam FileCategory category,
            @RequestParam(required = false) UUID projectId,
            Authentication authentication)
            throws IOException {
        UUID tenantId = TenantContext.requireTenantId();
        return fileService.upload(
                new UploadFileCommand(
                        tenantId,
                        projectId,
                        category,
                        requiredFilename(file),
                        file.getContentType(),
                        file.getSize(),
                        file.getInputStream(),
                        subjectId(authentication)));
    }

    @GetMapping
    @PreAuthorize("@fileAuthorization.canRead(authentication)")
    public FilePageResponse list(
            @RequestParam(required = false) FileCategory category,
            @RequestParam(required = false) FileStatus status,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String filename,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        var result =
                registry.list(
                        TenantContext.requireTenantId(),
                        category,
                        status,
                        projectId,
                        filename,
                        createdFrom,
                        createdTo,
                        page,
                        size,
                        sort);
        return new FilePageResponse(
                result.getContent().stream().map(FileResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @GetMapping("/{fileId}")
    @PreAuthorize("@fileAuthorization.canRead(authentication)")
    public FileResponse get(@PathVariable UUID fileId) {
        return FileResponse.from(fileService.get(TenantContext.requireTenantId(), fileId));
    }

    @GetMapping("/{fileId}/content")
    @PreAuthorize("@fileAuthorization.canRead(authentication)")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID fileId) {
        FileDownload download = fileService.openContent(TenantContext.requireTenantId(), fileId);
        FileMetadata metadata = download.metadata();

        HttpHeaders headers = new HttpHeaders();
        if (metadata.contentType() != null && !metadata.contentType().isBlank()) {
            try {
                headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
            } catch (IllegalArgumentException ignored) {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        if (metadata.sizeBytes() != null) {
            headers.setContentLength(metadata.sizeBytes());
        }
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(
                                metadata.originalFilename(),
                                java.nio.charset.StandardCharsets.UTF_8)
                        .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(download.content()));
    }

    @GetMapping("/{fileId}/download-url")
    @PreAuthorize("@fileAuthorization.canRead(authentication)")
    public DownloadUrlResponse downloadUrl(@PathVariable UUID fileId) {
        PresignedDownload result =
                fileService.generateDownloadUrl(TenantContext.requireTenantId(), fileId);
        return new DownloadUrlResponse(
                result.fileId(), result.url().toString(), result.ttl().toSeconds());
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@fileAuthorization.canDelete(authentication)")
    public void delete(@PathVariable UUID fileId) {
        fileService.delete(TenantContext.requireTenantId(), fileId);
    }

    private String requiredFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Original filename is required");
        }
        return filename;
    }

    private UUID subjectId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record FileResponse(
            UUID fileId,
            UUID projectId,
            FileCategory category,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            FileStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant deletedAt) {
        static FileResponse from(FileMetadata value) {
            return new FileResponse(
                    value.fileId(),
                    value.projectId(),
                    value.category(),
                    value.originalFilename(),
                    value.contentType(),
                    value.sizeBytes(),
                    value.status(),
                    value.createdAt(),
                    value.expiresAt(),
                    value.deletedAt());
        }

        static FileResponse from(StoredFile value) {
            return new FileResponse(
                    value.getId(),
                    value.getProjectId(),
                    value.getCategory(),
                    value.getOriginalFilename(),
                    value.getContentType(),
                    value.getSizeBytes(),
                    value.getStatus(),
                    value.getCreatedAt(),
                    value.getExpiresAt(),
                    value.getDeletedAt());
        }
    }

    public record FilePageResponse(
            List<FileResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    public record DownloadUrlResponse(UUID fileId, String url, long expiresInSeconds) {}
}
