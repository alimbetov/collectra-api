package io.collectra.api.template.preset;

import io.collectra.api.template.domain.TemplateChannel;

public record TemplatePresetContent(
        TemplatePresetCode code,
        String requestedLocale,
        String resolvedLocale,
        TemplateChannel channel,
        String subject,
        String content) {}
