package io.collectra.api.template.application;

import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.preset.TemplatePreset;
import io.collectra.api.template.preset.TemplatePresetCatalog;
import io.collectra.api.template.preset.TemplatePresetCode;
import io.collectra.api.template.preset.TemplatePresetContent;
import io.collectra.api.template.preset.TemplatePresetService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplatePresetApplicationService {
    private final TenantLocaleService tenantLocales;
    private final TemplatePresetCatalog catalog;
    private final TemplatePresetService presets;
    private final TemplateManagementService templates;

    public TemplatePresetApplicationService(
            TenantLocaleService tenantLocales,
            TemplatePresetCatalog catalog,
            TemplatePresetService presets,
            TemplateManagementService templates) {
        this.tenantLocales = tenantLocales;
        this.catalog = catalog;
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
        if (presetCode == null) throw new IllegalArgumentException("presetCode is required");
        if (channel == null) throw new IllegalArgumentException("channel is required");

        String targetLocale = tenantLocales.requireEnabled(tenantId, requestedLocale).getLocale();
        TemplatePreset presetDefinition = catalog.require(presetCode);
        TemplatePresetContent preset = presets.resolve(presetCode, channel, targetLocale);

        TemplateSelection selection =
                templateId == null
                        ? findOrCreatePresetTemplate(tenantId, presetDefinition)
                        : new TemplateSelection(templateId, false);

        TemplateVersion draft =
                templates.createVersion(
                        tenantId,
                        selection.templateId(),
                        targetLocale,
                        channel,
                        preset.subject(),
                        preset.content(),
                        null);

        return new AppliedTemplatePreset(
                presetCode,
                targetLocale,
                preset.resolvedLocale(),
                selection.created(),
                draft);
    }

    private TemplateSelection findOrCreatePresetTemplate(UUID tenantId, TemplatePreset preset) {
        DocumentTemplate existing =
                templates.list(tenantId).stream()
                        .filter(template -> template.getCode().equalsIgnoreCase(preset.code().name()))
                        .findFirst()
                        .orElse(null);
        if (existing != null) {
            return new TemplateSelection(existing.getId(), false);
        }

        DocumentTemplate created =
                templates.create(
                        tenantId,
                        preset.code().name(),
                        displayName(preset.code()),
                        preset.category());
        return new TemplateSelection(created.getId(), true);
    }

    private static String displayName(TemplatePresetCode code) {
        String[] words = code.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private record TemplateSelection(UUID templateId, boolean created) {}
}
