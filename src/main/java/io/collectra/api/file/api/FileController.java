package io.collectra.api.file.api;

import io.collectra.api.file.application.FileDownload;
import io.collectra.api.file.application.FileMetadata;
import io.collectra.api.file.application.FileService;
import io.collectra.api.file.application.PresignedDownload;
import io.collectra.api.file.application.UploadFileCommand;
import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.shared.tenant.TenantContext;
import java.io.IOException;
import java.util.UUID;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
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

    @GetMapping("/{fileId}")
    @PreAuthorize("@fileAuthorization.canRead(authentication)")
    public FileMetadata get(@PathVariable UUID fileId) {
        return fileService.get(TenantContext.requireTenantId(), fileId);
    }

    @GetMapping("/{fileId}/content")
    @PreAuthorize("@fileAuthorization.canRead(authentication)")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID fileId) {
        FileDownload download = fileService.openContent(TenantContext.requireTenantId(), fileId);
        StreamingResponseBody body =
                output -> {
                    try (var input = download.content()) {
                        input.transferTo(output);
                    }
                };

        HttpHeaders headers = new HttpHeaders();
        FileMetadata metadata = download.metadata();
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
                        .filename(metadata.originalFilename(), java.nio.charset.StandardCharsets.UTF_8)
                        .build());
        return ResponseEntity.ok().headers(headers).body(body);
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

    public record DownloadUrlResponse(UUID fileId, String url, long expiresInSeconds) {}
}
