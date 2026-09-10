package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.localization.application.SupportedLocaleService;
import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.localization.domain.SupportedLocale;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateLocaleResolverUnitTest {
    private final TenantLocaleService tenantLocales = mock(TenantLocaleService.class);
    private final SupportedLocaleService supportedLocales = mock(SupportedLocaleService.class);
    private final DocumentTemplateRepository templates = mock(DocumentTemplateRepository.class);
    private final TemplateVersionRepository versions = mock(TemplateVersionRepository.class);

    private TemplateLocaleResolver resolver;
    private UUID tenantId;
    private UUID templateId;

    @BeforeEach
    void setUp() {
        resolver = new TemplateLocaleResolver(tenantLocales, supportedLocales, templates, versions);
        tenantId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        when(templates.findByIdAndTenantId(templateId, tenantId))
                .thenReturn(Optional.of(mock(DocumentTemplate.class)));
        when(supportedLocales.canonicalize(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void resolvesExactPublishedTenantLocaleFirst() {
        SupportedLocale kk = locale("kk", "ru");
        when(supportedLocales.requireSupported("kk")).thenReturn(kk);
        when(tenantLocales.isEnabled(tenantId, "kk")).thenReturn(true);
        published("kk", true);

        ResolvedTemplateLocale resolved =
                resolver.resolve(tenantId, templateId, TemplateChannel.EMAIL, "kk");

        assertThat(resolved.resolvedLocale()).isEqualTo("kk");
        assertThat(resolved.source()).isEqualTo(TemplateLocaleResolutionSource.EXACT);
    }

    @Test
    void followsSupportedLocaleFallbackBeforeTenantDefault() {
        SupportedLocale kk = locale("kk", "ru");
        SupportedLocale ru = locale("ru", "en");
        when(supportedLocales.requireSupported("kk")).thenReturn(kk);
        when(supportedLocales.requireSupported("ru")).thenReturn(ru);
        when(tenantLocales.isEnabled(tenantId, "kk")).thenReturn(true);
        when(tenantLocales.isEnabled(tenantId, "ru")).thenReturn(true);
        published("kk", false);
        published("ru", true);

        ResolvedTemplateLocale resolved =
                resolver.resolve(tenantId, templateId, TemplateChannel.EMAIL, "kk");

        assertThat(resolved.resolvedLocale()).isEqualTo("ru");
        assertThat(resolved.source()).isEqualTo(TemplateLocaleResolutionSource.LOCALE_FALLBACK);
    }

    @Test
    void usesTenantDefaultWhenLocaleFallbacksHaveNoPublishedTemplate() {
        SupportedLocale zh = locale("zh-CN", "en");
        SupportedLocale en = locale("en", null);
        TenantLocale tenantDefault = mock(TenantLocale.class);
        when(tenantDefault.getLocale()).thenReturn("ru");
        when(supportedLocales.requireSupported("zh-CN")).thenReturn(zh);
        when(supportedLocales.requireSupported("en")).thenReturn(en);
        when(tenantLocales.requireDefault(tenantId)).thenReturn(tenantDefault);
        when(tenantLocales.isEnabled(tenantId, "zh-CN")).thenReturn(true);
        when(tenantLocales.isEnabled(tenantId, "en")).thenReturn(true);
        when(tenantLocales.isEnabled(tenantId, "ru")).thenReturn(true);
        published("zh-CN", false);
        published("en", false);
        published("ru", true);

        ResolvedTemplateLocale resolved =
                resolver.resolve(tenantId, templateId, TemplateChannel.PDF, "zh-CN");

        assertThat(resolved.resolvedLocale()).isEqualTo("ru");
        assertThat(resolved.source()).isEqualTo(TemplateLocaleResolutionSource.TENANT_DEFAULT);
    }

    @Test
    void failsWhenNoPublishedAllowedLocaleCanBeResolved() {
        SupportedLocale en = locale("en", null);
        TenantLocale tenantDefault = mock(TenantLocale.class);
        when(tenantDefault.getLocale()).thenReturn("en");
        when(supportedLocales.requireSupported("en")).thenReturn(en);
        when(tenantLocales.requireDefault(tenantId)).thenReturn(tenantDefault);
        when(tenantLocales.isEnabled(tenantId, "en")).thenReturn(true);
        published("en", false);

        assertThatThrownBy(
                        () -> resolver.resolve(
                                tenantId, templateId, TemplateChannel.EMAIL, "en"))
                .isInstanceOf(TemplateLocaleResolutionException.class)
                .hasMessageContaining("No published EMAIL template locale");
    }

    private SupportedLocale locale(String code, String fallback) {
        SupportedLocale locale = mock(SupportedLocale.class);
        when(locale.getCode()).thenReturn(code);
        when(locale.getFallbackLocale()).thenReturn(fallback);
        return locale;
    }

    private void published(String locale, boolean exists) {
        when(versions.existsByTemplateIdAndLocaleAndChannelAndStatus(
                        templateId, locale, TemplateChannel.EMAIL, TemplateVersionStatus.PUBLISHED))
                .thenReturn(exists);
        when(versions.existsByTemplateIdAndLocaleAndChannelAndStatus(
                        templateId, locale, TemplateChannel.PDF, TemplateVersionStatus.PUBLISHED))
                .thenReturn(exists);
    }
}
