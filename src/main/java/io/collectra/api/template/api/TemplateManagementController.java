package io.collectra.api.template.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.template.application.TemplateManagementService;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/templates")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class TemplateManagementController {
    private final TemplateManagementService service;
    public TemplateManagementController(TemplateManagementService service) { this.service=service; }

    @GetMapping
    @PreAuthorize("hasAuthority('TEMPLATE_READ')")
    List<TemplateResponse> list() { return service.list(tenant()).stream().map(TemplateResponse::from).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    TemplateResponse create(@Valid @RequestBody TemplateRequest request) {
        return TemplateResponse.from(service.create(tenant(),request.code(),request.name(),request.documentType()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    TemplateResponse rename(@PathVariable UUID id,@Valid @RequestBody RenameRequest request) {
        return TemplateResponse.from(service.rename(tenant(),id,request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    void archiveTemplate(@PathVariable UUID id) { service.archiveTemplate(tenant(),id); }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('TEMPLATE_READ')")
    List<VersionResponse> versions(@PathVariable UUID id) { return service.versions(tenant(),id).stream().map(VersionResponse::from).toList(); }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    VersionResponse createVersion(@PathVariable UUID id,@Valid @RequestBody VersionRequest request) {
        return VersionResponse.from(service.createVersion(tenant(),id,request.locale(),request.contentHtml(),request.stylesheet()));
    }

    @PutMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    VersionResponse update(@PathVariable UUID id,@Valid @RequestBody VersionContent request) {
        return VersionResponse.from(service.update(tenant(),id,request.contentHtml(),request.stylesheet()));
    }

    @PostMapping("/versions/{id}/validate")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    SourceSchemaManagementService.ValidationResult validate(@PathVariable UUID id) { return service.validate(tenant(),id); }

    @PostMapping("/versions/{id}/preview")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    TemplateRenderer.RenderResult preview(@PathVariable UUID id,@RequestBody JsonNode payload) { return service.preview(tenant(),id,payload); }

    @PostMapping("/versions/{id}/{action:publish|reopen|archive}")
    @PreAuthorize("hasAuthority('TEMPLATE_PUBLISH')")
    VersionResponse transition(@PathVariable UUID id,@PathVariable String action) {
        TemplateVersion value=switch(action) { case "publish"->service.publish(tenant(),id);
            case "reopen"->service.reopen(tenant(),id); case "archive"->service.archive(tenant(),id);
            default->throw new IllegalArgumentException("Unsupported transition"); };
        return VersionResponse.from(value);
    }

    private UUID tenant(){return TenantContext.requireTenantId();}
    record TemplateRequest(@NotBlank @Size(max=100) String code,@NotBlank @Size(max=200) String name,
            @NotBlank @Size(max=50) String documentType){}
    record RenameRequest(@NotBlank @Size(max=200) String name){}
    record VersionRequest(@NotBlank @Size(max=10) String locale,String contentHtml,String stylesheet){}
    record VersionContent(@NotBlank String contentHtml,String stylesheet){}
    record TemplateResponse(UUID id,String code,String name,String documentType,String status){
        static TemplateResponse from(DocumentTemplate v){return new TemplateResponse(v.getId(),v.getCode(),v.getName(),v.getDocumentType(),v.getStatus());}}
    record VersionResponse(UUID id,UUID templateId,int version,String locale,String contentHtml,String stylesheet,TemplateVersionStatus status){
        static VersionResponse from(TemplateVersion v){return new VersionResponse(v.getId(),v.getTemplateId(),v.getTemplateVersion(),v.getLocale(),v.getContentHtml(),v.getStylesheet(),v.getStatus());}}
}
