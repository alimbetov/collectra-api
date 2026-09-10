package io.collectra.api.template.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.localization.application.SupportedLocaleService;
import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.template.domain.TemplateChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplatePresetServiceUnitTest {
    private final SupportedLocaleService supportedLocales = mock(SupportedLocaleService.class);
    private TemplatePresetService service;

    @BeforeEach
    void setUp() {
        TemplatePresetCatalog catalog = new TemplatePresetCatalog(new ObjectMapper());
        catalog.load();
        service = new TemplatePresetService(catalog, supportedLocales);
    }

    @Test
    void keepsExactKazakhTranslation() {
        SupportedLocale kk = locale("kk", "ru");
        when(supportedLocales.canonicalize("kk")).thenReturn("kk");
        when(supportedLocales.requireSupported("kk")).thenReturn(kk);

        TemplatePresetContent resolved =
                service.resolve(
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        TemplateChannel.EMAIL,
                        "kk");

        assertThat(resolved.requestedLocale()).isEqualTo("kk");
        assertThat(resolved.resolvedLocale()).isEqualTo("kk");
        assertThat(resolved.subject()).contains("{{document.number}}");
        assertThat(resolved.content()).contains("{{customer.name}}");
    }

    @Test
    void fallsBackFromUzbekToRussianCatalogTranslation() {
        SupportedLocale uz = locale("uz", "ru");
        SupportedLocale ru = locale("ru", "en");
        when(supportedLocales.canonicalize("uz")).thenReturn("uz");
        when(supportedLocales.canonicalize("ru")).thenReturn("ru");
        when(supportedLocales.requireSupported("uz")).thenReturn(uz);
        when(supportedLocales.requireSupported("ru")).thenReturn(ru);

        TemplatePresetContent resolved =
                service.resolve(
                        TemplatePresetCode.INVOICE_REMINDER,
                        TemplateChannel.SMS,
                        "uz");

        assertThat(resolved.requestedLocale()).isEqualTo("uz");
        assertThat(resolved.resolvedLocale()).isEqualTo("ru");
        assertThat(resolved.subject()).isNull();
        assertThat(resolved.content()).contains("Напоминаем");
    }

    private SupportedLocale locale(String code, String fallback) {
        SupportedLocale locale = mock(SupportedLocale.class);
        when(locale.getCode()).thenReturn(code);
        when(locale.getFallbackLocale()).thenReturn(fallback);
        return locale;
    }
}
