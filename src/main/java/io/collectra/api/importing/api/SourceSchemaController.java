package io.collectra.api.importing.api;

import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.domain.SourceSchemaDefinition;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/source-schemas")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class SourceSchemaController {
    private final SourceSchemaManagementService service;
    public SourceSchemaController(SourceSchemaManagementService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_READ')")
    List<DefinitionResponse> list() { return service.list(tenant()).stream().map(DefinitionResponse::from).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    DefinitionResponse create(@Valid @RequestBody DefinitionRequest request) {
        return DefinitionResponse.from(service.create(tenant(), request.code(), request.name()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    DefinitionResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return DefinitionResponse.from(service.rename(tenant(), id, request.name()));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_READ')")
    List<VersionResponse> versions(@PathVariable UUID id) {
        return service.versions(tenant(), id).stream().map(VersionResponse::from).toList();
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    VersionResponse createVersion(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return VersionResponse.from(service.createVersion(tenant(), id, request.format()));
    }

    @GetMapping("/versions/{versionId}/fields")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_READ')")
    List<FieldResponse> fields(@PathVariable UUID versionId) {
        return service.fields(tenant(), versionId).stream().map(FieldResponse::from).toList();
    }

    @PostMapping("/versions/{versionId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    FieldResponse addField(@PathVariable UUID versionId, @Valid @RequestBody FieldRequest request) {
        return FieldResponse.from(service.addField(tenant(), versionId, request.sourcePath(),
                request.detectedType(), request.sampleValue(), request.required(), request.position()));
    }

    @PutMapping("/versions/{versionId}/fields/{fieldId}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    FieldResponse updateField(@PathVariable UUID versionId, @PathVariable UUID fieldId,
            @Valid @RequestBody FieldRequest request) {
        return FieldResponse.from(service.updateField(tenant(), versionId, fieldId,
                request.sourcePath(), request.detectedType(), request.sampleValue(),
                request.required(), request.position()));
    }

    @DeleteMapping("/versions/{versionId}/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    void deleteField(@PathVariable UUID versionId, @PathVariable UUID fieldId) {
        service.deleteField(tenant(), versionId, fieldId);
    }

    @PostMapping("/versions/{versionId}/validate")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    SourceSchemaManagementService.ValidationResult validate(@PathVariable UUID versionId) {
        return service.validate(tenant(), versionId);
    }

    @PostMapping("/versions/{versionId}/{action:publish|reopen|archive}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('SOURCE_SCHEMA_MANAGE')")
    VersionResponse transition(@PathVariable UUID versionId, @PathVariable String action) {
        SourceSchema value = switch (action) {
            case "publish" -> service.publish(tenant(), versionId);
            case "reopen" -> service.reopen(tenant(), versionId);
            case "archive" -> service.archive(tenant(), versionId);
            default -> throw new IllegalArgumentException("Unsupported transition");
        };
        return VersionResponse.from(value);
    }

    private UUID tenant() { return TenantContext.requireTenantId(); }
    record DefinitionRequest(@NotBlank @Size(max=100) String code, @NotBlank @Size(max=200) String name) {}
    record RenameRequest(@NotBlank @Size(max=200) String name) {}
    record VersionRequest(@NotNull SourceFormat format) {}
    record FieldRequest(@NotBlank @Size(max=300) String sourcePath, @Size(max=30) String detectedType,
            @Size(max=500) String sampleValue, boolean required, Integer position) {}
    record DefinitionResponse(UUID id, String code, String name) {
        static DefinitionResponse from(SourceSchemaDefinition v) { return new DefinitionResponse(v.getId(), v.getCode(), v.getName()); }
    }
    record VersionResponse(UUID id, UUID definitionId, int version, SourceFormat format, DefinitionStatus status) {
        static VersionResponse from(SourceSchema v) { return new VersionResponse(v.getId(), v.getDefinitionId(), v.getSchemaVersion(), v.getSourceFormat(), v.getStatus()); }
    }
    record FieldResponse(UUID id, String sourcePath, String detectedType, String sampleValue, boolean required, Integer position) {
        static FieldResponse from(SourceField v) { return new FieldResponse(v.getId(), v.getSourcePath(), v.getDetectedType(), v.getSampleValue(), v.isRequired(), v.getPosition()); }
    }
}
