package io.collectra.api.template.preset;

import io.collectra.api.localization.application.SupportedLocaleService;
import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.template.domain.TemplateChannel;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TemplatePresetService {
    private static final String PLATFORM_DEFAULT_LOCALE = "en";

    private final TemplatePresetCatalog catalog;
    private final SupportedLocaleService supportedLocales;

    public TemplatePresetService(
            TemplatePresetCatalog catalog,
            SupportedLocaleService supportedLocales) {
        this.catalog = catalog;
        this.supportedLocales = supportedLocales;
    }

    public TemplatePresetContent resolve(
            TemplatePresetCode code,
            TemplateChannel channel,
            String requestedLocale) {
        if (code == null) throw new IllegalArgumentException("code is required");
        if (channel == null) throw new IllegalArgumentException("channel is required");

        TemplatePreset preset = catalog.require(code);
        if (!preset.supportedChannels().contains(channel)) {
            throw new IllegalArgumentException(
                    "Template preset " + code + " does not support channel " + channel);
        }

        String requested = supportedLocales.canonicalize(requestedLocale);
        SupportedLocale locale = supportedLocales.requireSupported(requested);
        Set<String> visited = new HashSet<>();
        visited.add(requested);

        TemplatePresetTranslation translation = preset.translations().get(requested);
        if (translation != null) {
            return content(preset, requested, requested, channel, translation);
        }

        SupportedLocale current = locale;
        while (current.getFallbackLocale() != null && !current.getFallbackLocale().isBlank()) {
            String fallback = supportedLocales.canonicalize(current.getFallbackLocale());
            if (!visited.add(fallback)) {
                throw new IllegalStateException("Locale fallback cycle detected for " + requested);
            }
            current = supportedLocales.requireSupported(fallback);
            translation = preset.translations().get(fallback);
            if (translation != null) {
                return content(preset, requested, fallback, channel, translation);
            }
        }

        translation = preset.translations().get(PLATFORM_DEFAULT_LOCALE);
        if (translation != null) {
            return content(preset, requested, PLATFORM_DEFAULT_LOCALE, channel, translation);
        }

        throw new IllegalStateException(
                "No translation can be resolved for preset " + code + " and locale " + requested);
    }

    private static TemplatePresetContent content(
            TemplatePreset preset,
            String requestedLocale,
            String resolvedLocale,
            TemplateChannel channel,
            TemplatePresetTranslation translation) {
        String subject = channel == TemplateChannel.EMAIL ? translation.subject() : null;
        String body = switch (channel) {
            case EMAIL, PDF -> translation.html();
            case SMS, WHATSAPP, TELEGRAM -> translation.text();
        };
        if (channel == TemplateChannel.EMAIL && (subject == null || subject.isBlank())) {
            throw new IllegalStateException(
                    "Email subject is missing for preset " + preset.code() + " locale " + resolvedLocale);
        }
        return new TemplatePresetContent(
                preset.code(), requestedLocale, resolvedLocale, channel, subject, body);
    }
}
