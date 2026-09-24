package io.collectra.api.template.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.template.application.TemplateFrontendQueryService;
import io.collectra.api.template.application.TemplateFrontendQueryService.PageResponse;
import io.collectra.api.template.application.TemplateFrontendQueryService.TemplateDetail;
import io.collectra.api.template.application.TemplateFrontendQueryService.TemplateItem;
import io.collectra.api.template.application.TemplateFrontendQueryService.VersionItem;
import io.collectra.api.template.application.TemplateManagementService;
import io.collectra.api.template.application.TemplateMutationService;
import io.collectra.api.template.application.TemplateRenderer;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class TemplateManagementController {
    private final TemplateManagementService service;
    private final TemplateMutationService mutations;
    private final TemplateFrontendQueryService queries;

    public TemplateManagementController(
            TemplateManagementService service,
            TemplateMutationService mutations,
            TemplateFrontendQueryService queries) {
        this.service = service;
        this.mutations = mutations;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_READ')")
    PageResponse<TemplateItem> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.templates(
                tenant(),
                search,
                channel,
                status,
                locale,
                createdFrom,
                createdTo,
                page,
                size,
                sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    TemplateResponse create(@Valid @RequestBody TemplateRequest request) {
        return TemplateResponse.from(
                service.create(tenant(), request.code(), request.name(), request.documentType()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_READ')")
    TemplateDetail detail(@PathVariable UUID id) {
        return queries.template(tenant(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    TemplateResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return TemplateResponse.from(
                mutations.rename(tenant(), id, request.name(), request.revision()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    void archiveTemplate(@PathVariable UUID id, @RequestParam @Min(0) long revision) {
        mutations.archiveTemplate(tenant(), id, revision);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_READ')")
    PageResponse<VersionItem> versions(
            @PathVariable UUID id,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.versions(
                tenant(), id, channel, status, locale, createdFrom, createdTo, page, size);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    VersionResponse createVersion(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return VersionResponse.from(
                service.createVersion(
                        tenant(),
                        id,
                        request.locale(),
                        request.channel(),
                        request.subject(),
                        request.contentHtml(),
                        request.stylesheet()));
    }

    @PutMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    VersionResponse update(@PathVariable UUID id, @Valid @RequestBody VersionContent request) {
        return VersionResponse.from(
                mutations.update(
                        tenant(),
                        id,
                        request.subject(),
                        request.contentHtml(),
                        request.stylesheet(),
                        request.revision()));
    }

    @PostMapping("/versions/{id}/validate")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    SourceSchemaManagementService.ValidationResult validate(
            @PathVariable UUID id, @RequestParam @Min(0) long revision) {
        return mutations.validate(tenant(), id, revision);
    }

    @PostMapping("/versions/{id}/preview")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    TemplateRenderer.RenderResult preview(@PathVariable UUID id, @RequestBody JsonNode payload) {
        return service.preview(tenant(), id, payload);
    }

    @PostMapping("/versions/{id}/{action:publish|reopen|archive}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_PUBLISH')")
    VersionResponse transition(
            @PathVariable UUID id,
            @PathVariable String action,
            @RequestParam @Min(0) long revision) {
        TemplateVersion value =
                switch (action) {
                    case "publish" -> mutations.publish(tenant(), id, revision);
                    case "reopen" -> mutations.reopen(tenant(), id, revision);
                    case "archive" -> mutations.archive(tenant(), id, revision);
                    default -> throw new IllegalArgumentException("Unsupported transition");
                };
        return VersionResponse.from(value);
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    record TemplateRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String documentType) {}

    record RenameRequest(@NotBlank @Size(max = 200) String name, @NotNull @Min(0) Long revision) {}

    record VersionRequest(
            @NotBlank @Size(max = 35) String locale,
            TemplateChannel channel,
            @Size(max = 300) String subject,
            String contentHtml,
            String stylesheet) {}

    record VersionContent(
            @Size(max = 300) String subject,
            @NotBlank String contentHtml,
            String stylesheet,
            @NotNull @Min(0) Long revision) {}

    record TemplateResponse(
            UUID id,
            String code,
            String name,
            String documentType,
            String status,
            Instant createdAt,
            Instant updatedAt,
            long revision) {
        static TemplateResponse from(DocumentTemplate v) {
            return new TemplateResponse(
                    v.getId(),
                    v.getCode(),
                    v.getName(),
                    v.getDocumentType(),
                    v.getStatus(),
                    v.getCreatedAt(),
                    v.getUpdatedAt(),
                    v.getVersion());
        }
    }

    record VersionResponse(
            UUID id,
            UUID templateId,
            int version,
            int templateVersion,
            String locale,
            TemplateChannel channel,
            String subject,
            String contentHtml,
            String stylesheet,
            TemplateVersionStatus status,
            Instant createdAt,
            Instant updatedAt,
            long revision) {
        static VersionResponse from(TemplateVersion v) {
            return new VersionResponse(
                    v.getId(),
                    v.getTemplateId(),
                    v.getTemplateVersion(),
                    v.getTemplateVersion(),
                    v.getLocale(),
                    v.getChannel(),
                    v.getSubject(),
                    v.getContentHtml(),
                    v.getStylesheet(),
                    v.getStatus(),
                    v.getCreatedAt(),
                    v.getUpdatedAt(),
                    v.getVersion());
        }
    }
}
