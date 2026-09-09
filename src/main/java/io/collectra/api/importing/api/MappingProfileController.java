package io.collectra.api.importing.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.importing.application.MappingExecutionService;
import io.collectra.api.importing.application.MappingProfileManagementService;
import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.importing.domain.*;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/mapping-profiles")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class MappingProfileController {
    private final MappingProfileManagementService service;
    public MappingProfileController(MappingProfileManagementService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_READ')")
    List<DefinitionResponse> list() { return service.list(tenant()).stream().map(DefinitionResponse::from).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    DefinitionResponse create(@Valid @RequestBody DefinitionRequest request) {
        return DefinitionResponse.from(service.create(tenant(), request.code(), request.name(), request.documentType()));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_READ')")
    List<VersionResponse> versions(@PathVariable UUID id) { return service.versions(tenant(), id).stream().map(VersionResponse::from).toList(); }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    VersionResponse createVersion(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return VersionResponse.from(service.createVersion(tenant(), id, request.sourceSchemaVersionId()));
    }

    @GetMapping("/versions/{versionId}/rules")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_READ')")
    List<RuleResponse> rules(@PathVariable UUID versionId) { return service.rules(tenant(), versionId).stream().map(RuleResponse::from).toList(); }

    @PostMapping("/versions/{versionId}/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    RuleResponse addRule(@PathVariable UUID versionId, @Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(service.addRule(tenant(), versionId, request.sourceFieldId(),
                request.targetFieldId(), request.transformation(), request.defaultValue(), request.required()));
    }

    @PutMapping("/versions/{versionId}/rules/{ruleId}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    RuleResponse updateRule(@PathVariable UUID versionId, @PathVariable UUID ruleId,
            @Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(service.updateRule(tenant(), versionId, ruleId, request.sourceFieldId(),
                request.targetFieldId(), request.transformation(), request.defaultValue(), request.required()));
    }

    @DeleteMapping("/versions/{versionId}/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    void deleteRule(@PathVariable UUID versionId, @PathVariable UUID ruleId) { service.deleteRule(tenant(), versionId, ruleId); }

    @PostMapping("/versions/{versionId}/validate")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    SourceSchemaManagementService.ValidationResult validate(@PathVariable UUID versionId) { return service.validate(tenant(), versionId); }

    @PostMapping(value="/versions/{versionId}/test", consumes="multipart/form-data")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    MappingExecutionService.MappingResult test(@PathVariable UUID versionId, @RequestPart("file") MultipartFile file) throws IOException {
        return service.test(tenant(), versionId, file.getBytes());
    }

    @PostMapping("/versions/{versionId}/rules/{ruleId}/test")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    MappingExecutionService.RuleTestResult testRule(@PathVariable UUID versionId,
            @PathVariable UUID ruleId, @RequestBody RuleTestRequest request) {
        return service.testRule(tenant(), versionId, ruleId, request.sourceValue());
    }

    @PostMapping("/versions/{versionId}/{action:publish|reopen|archive}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('MAPPING_PROFILE_MANAGE')")
    VersionResponse transition(@PathVariable UUID versionId, @PathVariable String action) {
        MappingProfile value = switch (action) {
            case "publish" -> service.publish(tenant(), versionId);
            case "reopen" -> service.reopen(tenant(), versionId);
            case "archive" -> service.archive(tenant(), versionId);
            default -> throw new IllegalArgumentException("Unsupported transition");
        };
        return VersionResponse.from(value);
    }

    private UUID tenant() { return TenantContext.requireTenantId(); }
    record DefinitionRequest(@NotBlank @Size(max=100) String code, @NotBlank @Size(max=200) String name,
            @NotBlank @Size(max=50) String documentType) {}
    record VersionRequest(@NotNull UUID sourceSchemaVersionId) {}
    record RuleRequest(@NotNull UUID sourceFieldId, @NotNull UUID targetFieldId,
            JsonNode transformation, @Size(max=500) String defaultValue, boolean required) {}
    record RuleTestRequest(JsonNode sourceValue) {}
    record DefinitionResponse(UUID id, String code, String name, String documentType) {
        static DefinitionResponse from(MappingProfileDefinition v) { return new DefinitionResponse(v.getId(), v.getCode(), v.getName(), v.getDocumentType()); }
    }
    record VersionResponse(UUID id, UUID definitionId, UUID sourceSchemaVersionId, int version,
            String documentType, DefinitionStatus status) {
        static VersionResponse from(MappingProfile v) { return new VersionResponse(v.getId(), v.getDefinitionId(), v.getSourceSchemaId(), v.getProfileVersion(), v.getDocumentType(), v.getStatus()); }
    }
    record RuleResponse(UUID id, UUID sourceFieldId, UUID targetFieldId, JsonNode transformation,
            String defaultValue, boolean required) {
        static RuleResponse from(MappingRule v) { return new RuleResponse(v.getId(), v.getSourceFieldId(), v.getTargetFieldId(), v.getTransformation(), v.getDefaultValue(), v.isRequired()); }
    }
}
