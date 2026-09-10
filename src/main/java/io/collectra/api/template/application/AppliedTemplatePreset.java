package io.collectra.api.template.application;

import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.preset.TemplatePresetCode;

public record AppliedTemplatePreset(
        TemplatePresetCode presetCode,
        String requestedLocale,
        String seedLocale,
        TemplateVersion version) {}
