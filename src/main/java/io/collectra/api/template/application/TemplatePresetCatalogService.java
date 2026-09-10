package io.collectra.api.template.application;

import io.collectra.api.localization.application.SupportedLocaleService;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.preset.TemplatePreset;
import io.collectra.api.template.preset.TemplatePresetCatalog;
import io.collectra.api.template.preset.TemplatePresetCode;
import io.collectra.api.template.preset.TemplatePresetContent;
import io.collectra.api.template.preset.TemplatePresetService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplatePresetCatalogService {
    private static final String PLATFORM_DEFAULT_LOCALE = "en";

    private final TemplatePresetCatalog catalog;
    private final TemplatePresetService presets;
    private final TenantLocaleService tenantLocales;
    private final SupportedLocaleService supportedLocales;

    public TemplatePresetCatalogService(
            TemplatePresetCatalog catalog,
            TemplatePresetService presets,
            TenantLocaleService tenantLocales,
            SupportedLocaleService supportedLocales) {
        this.catalog = catalog;
        this.presets = presets;
        this.tenantLocales = tenantLocales;
        this.supportedLocales = supportedLocales;
    }

    @Transactional(readOnly = true)
    public CatalogView catalog(UUID tenantId) {
        List<TenantLocale> enabledTenantLocales = tenantLocales.listEnabled(tenantId);
        if (enabledTenantLocales.isEmpty()) {
            throw new IllegalStateException("Tenant has no enabled locales: " + tenantId);
        }

        String defaultLocale = tenantLocales.requireDefault(tenantId).getLocale();
        Map<String, SupportedLocale> supportedByCode = new HashMap<>();
        for (SupportedLocale locale : supportedLocales.listEnabled()) {
            supportedByCode.put(locale.getCode(), locale);
        }

        List<LocaleView> locales =
                enabledTenantLocales.stream()
                        .map(locale -> localeView(locale, supportedByCode, defaultLocale))
                        .toList();

        List<PresetView> presetViews =
                catalog.list().stream()
                        .map(preset -> presetView(preset, enabledTenantLocales, supportedByCode))
                        .toList();

        List<String> categories =
                presetViews.stream().map(PresetView::category).distinct().sorted().toList();

        return new CatalogView(defaultLocale, locales, categories, presetViews);
    }

    @Transactional(readOnly = true)
    public TemplatePresetContent preview(
            UUID tenantId,
            TemplatePresetCode presetCode,
            TemplateChannel channel,
            String requestedLocale) {
        String targetLocale = tenantLocales.requireEnabled(tenantId, requestedLocale).getLocale();
        return presets.resolve(presetCode, channel, targetLocale);
    }

    private LocaleView localeView(
            TenantLocale tenantLocale,
            Map<String, SupportedLocale> supportedByCode,
            String defaultLocale) {
        SupportedLocale supported = supportedByCode.get(tenantLocale.getLocale());
        if (supported == null) {
            throw new IllegalStateException(
                    "Enabled tenant locale is not globally supported: " + tenantLocale.getLocale());
        }
        return new LocaleView(
                supported.getCode(),
                supported.getDisplayName(),
                supported.getNativeName(),
                supported.getDirection().name(),
                supported.getFontProfile().name(),
                supported.getCode().equals(defaultLocale));
    }

    private PresetView presetView(
            TemplatePreset preset,
            List<TenantLocale> enabledTenantLocales,
            Map<String, SupportedLocale> supportedByCode) {
        List<ChannelView> channels =
                preset.supportedChannels().stream()
                        .map(
                                channel ->
                                        new ChannelView(
                                                channel,
                                                contentType(channel),
                                                enabledTenantLocales.stream()
                                                        .map(
                                                                locale ->
                                                                        compatibility(
                                                                                preset,
                                                                                channel,
                                                                                locale.getLocale(),
                                                                                supportedByCode))
                                                        .toList()))
                        .toList();

        return new PresetView(
                preset.code(),
                displayName(preset.code()),
                "templatePreset." + preset.code().name().toLowerCase(Locale.ROOT),
                preset.category(),
                channels);
    }

    private LocaleCompatibility compatibility(
            TemplatePreset preset,
            TemplateChannel channel,
            String requestedLocale,
            Map<String, SupportedLocale> supportedByCode) {
        String resolved = resolveLocale(preset, requestedLocale, supportedByCode);
        return new LocaleCompatibility(
                requestedLocale,
                resolved,
                requestedLocale.equals(resolved),
                true,
                channel == TemplateChannel.EMAIL);
    }

    private String resolveLocale(
            TemplatePreset preset,
            String requestedLocale,
            Map<String, SupportedLocale> supportedByCode) {
        if (preset.translations().containsKey(requestedLocale)) {
            return requestedLocale;
        }

        Set<String> visited = new HashSet<>();
        String currentCode = requestedLocale;
        visited.add(currentCode);
        while (true) {
            SupportedLocale current = supportedByCode.get(currentCode);
            if (current == null
                    || current.getFallbackLocale() == null
                    || current.getFallbackLocale().isBlank()) {
                break;
            }
            String fallback = supportedLocales.canonicalize(current.getFallbackLocale());
            if (!visited.add(fallback)) {
                throw new IllegalStateException(
                        "Locale fallback cycle detected for " + requestedLocale);
            }
            if (preset.translations().containsKey(fallback)) {
                return fallback;
            }
            currentCode = fallback;
        }

        if (preset.translations().containsKey(PLATFORM_DEFAULT_LOCALE)) {
            return PLATFORM_DEFAULT_LOCALE;
        }
        throw new IllegalStateException(
                "No translation can be resolved for preset "
                        + preset.code()
                        + " and locale "
                        + requestedLocale);
    }

    public static String contentType(TemplateChannel channel) {
        return switch (channel) {
            case EMAIL, PDF -> "HTML";
            case SMS, WHATSAPP, TELEGRAM -> "TEXT";
        };
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

    public record CatalogView(
            String defaultLocale,
            List<LocaleView> locales,
            List<String> categories,
            List<PresetView> presets) {}

    public record LocaleView(
            String code,
            String displayName,
            String nativeName,
            String direction,
            String fontProfile,
            boolean defaultLocale) {}

    public record PresetView(
            TemplatePresetCode code,
            String displayName,
            String i18nKey,
            String category,
            List<ChannelView> channels) {}

    public record ChannelView(
            TemplateChannel channel,
            String contentType,
            List<LocaleCompatibility> locales) {}

    public record LocaleCompatibility(
            String requestedLocale,
            String seedLocale,
            boolean exactTranslation,
            boolean available,
            boolean subjectSupported) {}
}
