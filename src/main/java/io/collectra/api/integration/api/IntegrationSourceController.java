package io.collectra.api.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.integration.application.IntegrationSourceService;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integration/sources")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class IntegrationSourceController {
    private final IntegrationSourceService service;
    public IntegrationSourceController(IntegrationSourceService service){this.service=service;}

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_READ')")
    List<IntegrationSourceService.SourceResponse> list(){return service.list(TenantContext.requireTenantId());}

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_READ')")
    IntegrationSourceService.SourceResponse get(@PathVariable UUID id){return service.get(TenantContext.requireTenantId(),id);}

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_MANAGE')")
    IntegrationSourceService.SourceResponse create(@Valid @RequestBody CreateRequest r){
        return service.create(TenantContext.requireTenantId(),new IntegrationSourceService.CreateCommand(r.code(),r.name(),r.serviceClientId(),r.sourceSchemaDefinitionId(),r.mappingProfileDefinitionId(),r.processingMode(),r.headerMapping(),r.resourcePolicy(),r.routingConfig()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_MANAGE')")
    IntegrationSourceService.SourceResponse update(@PathVariable UUID id,@Valid @RequestBody UpdateRequest r){
        return service.update(TenantContext.requireTenantId(),id,r.version(),new IntegrationSourceService.UpdateCommand(r.name(),r.serviceClientId(),r.sourceSchemaDefinitionId(),r.mappingProfileDefinitionId(),r.processingMode(),r.headerMapping(),r.resourcePolicy(),r.routingConfig()));
    }

    @GetMapping("/{id}/readiness")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_READ')")
    IntegrationSourceService.ReadinessResponse readiness(@PathVariable UUID id){return service.validate(TenantContext.requireTenantId(),id);}

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_MANAGE')")
    IntegrationSourceService.SourceResponse activate(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.activate(TenantContext.requireTenantId(),id,r.version());}
    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_MANAGE')")
    IntegrationSourceService.SourceResponse suspend(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.suspend(TenantContext.requireTenantId(),id,r.version());}
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('INTEGRATION_SOURCE_MANAGE')")
    IntegrationSourceService.SourceResponse archive(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.archive(TenantContext.requireTenantId(),id,r.version());}

    record CreateRequest(@NotBlank String code,@NotBlank String name,@NotNull UUID serviceClientId,@NotNull UUID sourceSchemaDefinitionId,@NotNull UUID mappingProfileDefinitionId,String processingMode,JsonNode headerMapping,JsonNode resourcePolicy,JsonNode routingConfig){}
    record UpdateRequest(@NotBlank String name,@NotNull UUID serviceClientId,@NotNull UUID sourceSchemaDefinitionId,@NotNull UUID mappingProfileDefinitionId,String processingMode,JsonNode headerMapping,JsonNode resourcePolicy,JsonNode routingConfig,long version){}
    record VersionRequest(long version){}
}
