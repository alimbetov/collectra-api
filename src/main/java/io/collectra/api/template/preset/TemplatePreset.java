package io.collectra.api.template.preset;

import io.collectra.api.template.domain.TemplateChannel;
import java.util.List;
import java.util.Map;

public record TemplatePreset(
        TemplatePresetCode code,
        String category,
        List<TemplateChannel> supportedChannels,
        Map<String, TemplatePresetTranslation> translations) {}
