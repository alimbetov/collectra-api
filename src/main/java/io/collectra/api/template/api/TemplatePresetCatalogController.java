package io.collectra.api.template.api;

import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.template.application.AppliedTemplatePreset;
import io.collectra.api.template.application.TemplatePresetApplicationService;
import io.collectra.api.template.application.TemplatePresetCatalogService;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.preset.TemplatePresetCode;
import io.collectra.api.template.preset.TemplatePresetContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/template-presets")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class TemplatePresetCatalogController {
    private final TemplatePresetCatalogService catalog;
    private final TemplatePresetApplicationService application;

    public TemplatePresetCatalogController(
            TemplatePresetCatalogService catalog,
            TemplatePresetApplicationService application) {
        this.catalog = catalog;
        this.application = application;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_READ')")
    TemplatePresetCatalogService.CatalogView catalog() {
        return catalog.catalog(tenant());
    }

    @PostMapping("/{presetCode}/preview")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_READ')")
    PreviewResponse preview(
            @PathVariable TemplatePresetCode presetCode,
            @Valid @RequestBody PreviewRequest request) {
        return PreviewResponse.from(
                catalog.preview(tenant(), presetCode, request.channel(), request.locale()));
    }

    @PostMapping("/{presetCode}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    ApplyResponse apply(
            @PathVariable TemplatePresetCode presetCode,
            @Valid @RequestBody ApplyRequest request) {
        AppliedTemplatePreset applied =
                application.apply(
                        tenant(),
                        request.templateId(),
                        presetCode,
                        request.channel(),
                        request.locale());
        return ApplyResponse.from(applied);
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    record PreviewRequest(
            @NotBlank @Size(max = 35) String locale, @NotNull TemplateChannel channel) {}

    record ApplyRequest(
            UUID templateId,
            @NotBlank @Size(max = 35) String locale,
            @NotNull TemplateChannel channel) {}

    record PreviewResponse(
            TemplatePresetCode presetCode,
            String requestedLocale,
            String seedLocale,
            TemplateChannel channel,
            String contentType,
            boolean fallbackUsed,
            String subject,
            String content) {
        static PreviewResponse from(TemplatePresetContent content) {
            return new PreviewResponse(
                    content.code(),
                    content.requestedLocale(),
                    content.resolvedLocale(),
                    content.channel(),
                    TemplatePresetCatalogService.contentType(content.channel()),
                    !content.requestedLocale().equals(content.resolvedLocale()),
                    content.subject(),
                    content.content());
        }
    }

    record ApplyResponse(
            TemplatePresetCode presetCode,
            String requestedLocale,
            String seedLocale,
            boolean fallbackUsed,
            boolean templateCreated,
            UUID templateId,
            UUID versionId,
            int version,
            TemplateChannel channel,
            String contentType,
            TemplateVersionStatus status,
            String subject,
            String content,
            NextActions nextActions) {
        static ApplyResponse from(AppliedTemplatePreset applied) {
            TemplateVersion version = applied.version();
            return new ApplyResponse(
                    applied.presetCode(),
                    applied.requestedLocale(),
                    applied.seedLocale(),
                    !applied.requestedLocale().equals(applied.seedLocale()),
                    applied.templateCreated(),
                    version.getTemplateId(),
                    version.getId(),
                    version.getTemplateVersion(),
                    version.getChannel(),
                    TemplatePresetCatalogService.contentType(version.getChannel()),
                    version.getStatus(),
                    version.getSubject(),
                    version.getContentHtml(),
                    new NextActions(
                            "/api/v1/template-builder/versions/" + version.getId(),
                            "/api/v1/template-builder/versions/" + version.getId(),
                            "/api/v1/template-builder/versions/" + version.getId() + "/validate",
                            "/api/v1/template-builder/versions/" + version.getId() + "/publish"));
        }
    }

    record NextActions(
            String getDraft,
            String updateDraft,
            String validateDraft,
            String publishDraft) {}
}
