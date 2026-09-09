package io.collectra.api.file.api;

import io.collectra.api.file.application.FileCleanupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/files")
public class InternalFileController {
    private final FileCleanupService cleanupService;

    public InternalFileController(FileCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @PostMapping("/cleanup")
    @PreAuthorize("@fileAuthorization.canAdmin(authentication)")
    public FileCleanupService.CleanupResult cleanup() {
        return cleanupService.cleanupExpiredFiles();
    }
}
