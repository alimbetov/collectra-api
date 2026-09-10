package io.collectra.api.template.api;

import io.collectra.api.shared.tenant.TenantContext;
import io.collectra.api.template.application.AppliedTemplatePreset;
import io.collectra.api.template.application.TemplatePresetApplicationService;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.preset.TemplatePresetCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates/{templateId}/presets")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class TemplatePresetController {
    private final TemplatePresetApplicationService service;

    public TemplatePresetController(TemplatePresetApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{presetCode}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('TEMPLATE_MANAGE')")
    AppliedPresetResponse apply(
            @PathVariable UUID templateId,
            @PathVariable TemplatePresetCode presetCode,
            @Valid @RequestBody ApplyPresetRequest request) {
        return AppliedPresetResponse.from(
                service.apply(
                        TenantContext.requireTenantId(),
                        templateId,
                        presetCode,
                        request.channel(),
                        request.locale()));
    }

    record ApplyPresetRequest(
            @NotBlank @Size(max = 35) String locale,
            TemplateChannel channel) {}

    record AppliedPresetResponse(
            TemplatePresetCode presetCode,
            String requestedLocale,
            String seedLocale,
            UUID versionId,
            UUID templateId,
            int version,
            TemplateChannel channel,
            String subject,
            String contentHtml,
            TemplateVersionStatus status) {
        static AppliedPresetResponse from(AppliedTemplatePreset applied) {
            TemplateVersion v = applied.version();
            return new AppliedPresetResponse(
                    applied.presetCode(),
                    applied.requestedLocale(),
                    applied.seedLocale(),
                    v.getId(),
                    v.getTemplateId(),
                    v.getTemplateVersion(),
                    v.getChannel(),
                    v.getSubject(),
                    v.getContentHtml(),
                    v.getStatus());
        }
    }
}
