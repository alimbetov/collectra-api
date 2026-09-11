package io.collectra.api.importing.api;

import io.collectra.api.importing.application.BusinessImportService;
import io.collectra.api.shared.tenant.TenantContext;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/business-imports")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class BusinessImportController {
    private final BusinessImportService service;

    public BusinessImportController(BusinessImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/mapping-profiles/{versionId}", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    BusinessImportService.ImportResult importFile(
            @PathVariable UUID versionId, @RequestPart("file") MultipartFile file)
            throws IOException {
        return service.importRecords(TenantContext.requireTenantId(), versionId, file.getBytes());
    }
}
