package io.collectra.api.template.application;

import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.preset.TemplatePresetCode;
import io.collectra.api.template.preset.TemplatePresetContent;
import io.collectra.api.template.preset.TemplatePresetService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplatePresetApplicationService {
    private final TenantLocaleService tenantLocales;
    private final TemplatePresetService presets;
    private final TemplateManagementService templates;

    public TemplatePresetApplicationService(
            TenantLocaleService tenantLocales,
            TemplatePresetService presets,
            TemplateManagementService templates) {
        this.tenantLocales = tenantLocales;
        this.presets = presets;
        this.templates = templates;
    }

    @Transactional
    public AppliedTemplatePreset apply(
            UUID tenantId,
            UUID templateId,
            TemplatePresetCode presetCode,
            TemplateChannel channel,
            String requestedLocale) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (templateId == null) throw new IllegalArgumentException("templateId is required");
        if (presetCode == null) throw new IllegalArgumentException("presetCode is required");
        if (channel == null) throw new IllegalArgumentException("channel is required");

        String targetLocale = tenantLocales.requireEnabled(tenantId, requestedLocale).getLocale();
        TemplatePresetContent preset = presets.resolve(presetCode, channel, targetLocale);

        TemplateVersion draft =
                templates.createVersion(
                        tenantId,
                        templateId,
                        targetLocale,
                        channel,
                        preset.subject(),
                        preset.content(),
                        null);

        return new AppliedTemplatePreset(
                presetCode,
                targetLocale,
                preset.resolvedLocale(),
                draft);
    }
}
