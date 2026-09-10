package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.localization.application.SupportedLocaleService;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.localization.domain.FontProfileCode;
import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.localization.domain.TextDirection;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.preset.TemplatePreset;
import io.collectra.api.template.preset.TemplatePresetCatalog;
import io.collectra.api.template.preset.TemplatePresetCode;
import io.collectra.api.template.preset.TemplatePresetService;
import io.collectra.api.template.preset.TemplatePresetTranslation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplatePresetCatalogServiceUnitTest {

    @Test
    void exposesTenantLocalesAndFallbackSeedWithoutNPlusOneResolution() {
        TemplatePresetCatalog catalog = mock(TemplatePresetCatalog.class);
        TemplatePresetService presets = mock(TemplatePresetService.class);
        TenantLocaleService tenantLocales = mock(TenantLocaleService.class);
        SupportedLocaleService supportedLocales = mock(SupportedLocaleService.class);
        TemplatePresetCatalogService service =
                new TemplatePresetCatalogService(catalog, presets, tenantLocales, supportedLocales);

        UUID tenantId = UUID.randomUUID();
        TenantLocale uzTenant = tenantLocale("uz");
        TenantLocale ruTenant = tenantLocale("ru");
        when(tenantLocales.listEnabled(tenantId)).thenReturn(List.of(uzTenant, ruTenant));
        when(tenantLocales.requireDefault(tenantId)).thenReturn(ruTenant);

        SupportedLocale uz = supportedLocale("uz", "Uzbek", "O‘zbekcha", "ru");
        SupportedLocale ru = supportedLocale("ru", "Russian", "Русский", "en");
        SupportedLocale en = supportedLocale("en", "English", "English", null);
        when(supportedLocales.listEnabled()).thenReturn(List.of(uz, ru, en));
        when(supportedLocales.canonicalize("ru")).thenReturn("ru");

        TemplatePreset preset =
                new TemplatePreset(
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        "PAYMENT",
                        List.of(TemplateChannel.EMAIL, TemplateChannel.SMS),
                        Map.of(
                                "en",
                                translation("English subject", "English text", "<p>English</p>"),
                                "ru",
                                translation("Русская тема", "Русский текст", "<p>Русский</p>")));
        when(catalog.list()).thenReturn(List.of(preset));

        TemplatePresetCatalogService.CatalogView result = service.catalog(tenantId);

        assertThat(result.defaultLocale()).isEqualTo("ru");
        assertThat(result.locales()).extracting(TemplatePresetCatalogService.LocaleView::code)
                .containsExactly("uz", "ru");
        assertThat(result.categories()).containsExactly("PAYMENT");
        assertThat(result.presets()).hasSize(1);

        TemplatePresetCatalogService.PresetView paymentOverdue = result.presets().get(0);
        assertThat(paymentOverdue.code()).isEqualTo(TemplatePresetCode.PAYMENT_OVERDUE);
        assertThat(paymentOverdue.i18nKey()).isEqualTo("templatePreset.payment_overdue");

        TemplatePresetCatalogService.ChannelView email = paymentOverdue.channels().get(0);
        assertThat(email.channel()).isEqualTo(TemplateChannel.EMAIL);
        assertThat(email.contentType()).isEqualTo("HTML");
        assertThat(email.locales().get(0).requestedLocale()).isEqualTo("uz");
        assertThat(email.locales().get(0).seedLocale()).isEqualTo("ru");
        assertThat(email.locales().get(0).exactTranslation()).isFalse();
        assertThat(email.locales().get(0).subjectSupported()).isTrue();

        TemplatePresetCatalogService.ChannelView sms = paymentOverdue.channels().get(1);
        assertThat(sms.contentType()).isEqualTo("TEXT");
        assertThat(sms.locales().get(0).subjectSupported()).isFalse();
    }

    private TenantLocale tenantLocale(String locale) {
        TenantLocale value = mock(TenantLocale.class);
        when(value.getLocale()).thenReturn(locale);
        return value;
    }

    private SupportedLocale supportedLocale(
            String code, String displayName, String nativeName, String fallback) {
        SupportedLocale value = mock(SupportedLocale.class);
        when(value.getCode()).thenReturn(code);
        when(value.getDisplayName()).thenReturn(displayName);
        when(value.getNativeName()).thenReturn(nativeName);
        when(value.getDirection()).thenReturn(TextDirection.LTR);
        when(value.getFontProfile()).thenReturn(FontProfileCode.LATIN_CYRILLIC);
        when(value.getFallbackLocale()).thenReturn(fallback);
        return value;
    }

    private TemplatePresetTranslation translation(String subject, String text, String html) {
        return new TemplatePresetTranslation(subject, text, html);
    }
}
