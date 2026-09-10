package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.preset.TemplatePreset;
import io.collectra.api.template.preset.TemplatePresetCatalog;
import io.collectra.api.template.preset.TemplatePresetCode;
import io.collectra.api.template.preset.TemplatePresetContent;
import io.collectra.api.template.preset.TemplatePresetService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplatePresetApplicationServiceUnitTest {

    @Test
    void createsUzbekDraftFromRussianFallbackWithoutChangingTargetLocale() {
        TenantLocaleService tenantLocales = mock(TenantLocaleService.class);
        TemplatePresetCatalog catalog = mock(TemplatePresetCatalog.class);
        TemplatePresetService presets = mock(TemplatePresetService.class);
        TemplateManagementService templates = mock(TemplateManagementService.class);
        TemplatePresetApplicationService service =
                new TemplatePresetApplicationService(tenantLocales, catalog, presets, templates);

        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        TenantLocale uz = mock(TenantLocale.class);
        when(uz.getLocale()).thenReturn("uz");
        when(tenantLocales.requireEnabled(tenantId, "uz")).thenReturn(uz);
        when(catalog.require(TemplatePresetCode.PAYMENT_OVERDUE))
                .thenReturn(preset(TemplatePresetCode.PAYMENT_OVERDUE));

        TemplatePresetContent resolved =
                new TemplatePresetContent(
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        "uz",
                        "ru",
                        TemplateChannel.EMAIL,
                        "Просрочена оплата {{document.number}}",
                        "<p>Оплата документа <strong>{{document.number}}</strong> просрочена.</p>");
        when(presets.resolve(
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        TemplateChannel.EMAIL,
                        "uz"))
                .thenReturn(resolved);

        TemplateVersion draft = mock(TemplateVersion.class);
        when(draft.getStatus()).thenReturn(TemplateVersionStatus.DRAFT);
        when(templates.createVersion(
                        tenantId,
                        templateId,
                        "uz",
                        TemplateChannel.EMAIL,
                        resolved.subject(),
                        resolved.content(),
                        null))
                .thenReturn(draft);

        AppliedTemplatePreset applied =
                service.apply(
                        tenantId,
                        templateId,
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        TemplateChannel.EMAIL,
                        "uz");

        assertThat(applied.requestedLocale()).isEqualTo("uz");
        assertThat(applied.seedLocale()).isEqualTo("ru");
        assertThat(applied.templateCreated()).isFalse();
        assertThat(applied.version().getStatus()).isEqualTo(TemplateVersionStatus.DRAFT);
        verify(templates)
                .createVersion(
                        tenantId,
                        templateId,
                        "uz",
                        TemplateChannel.EMAIL,
                        resolved.subject(),
                        resolved.content(),
                        isNull());
    }

    @Test
    void createsTenantTemplateAutomaticallyWhenTemplateIdIsOmitted() {
        TenantLocaleService tenantLocales = mock(TenantLocaleService.class);
        TemplatePresetCatalog catalog = mock(TemplatePresetCatalog.class);
        TemplatePresetService presets = mock(TemplatePresetService.class);
        TemplateManagementService templates = mock(TemplateManagementService.class);
        TemplatePresetApplicationService service =
                new TemplatePresetApplicationService(tenantLocales, catalog, presets, templates);

        UUID tenantId = UUID.randomUUID();
        UUID createdTemplateId = UUID.randomUUID();
        TenantLocale kk = mock(TenantLocale.class);
        when(kk.getLocale()).thenReturn("kk");
        when(tenantLocales.requireEnabled(tenantId, "kk")).thenReturn(kk);

        TemplatePreset preset = preset(TemplatePresetCode.PAYMENT_OVERDUE);
        when(catalog.require(TemplatePresetCode.PAYMENT_OVERDUE)).thenReturn(preset);
        when(templates.list(tenantId)).thenReturn(List.of());

        DocumentTemplate createdTemplate = mock(DocumentTemplate.class);
        when(createdTemplate.getId()).thenReturn(createdTemplateId);
        when(templates.create(tenantId, "PAYMENT_OVERDUE", "Payment Overdue", "PAYMENT"))
                .thenReturn(createdTemplate);

        TemplatePresetContent resolved =
                new TemplatePresetContent(
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        "kk",
                        "kk",
                        TemplateChannel.EMAIL,
                        "Төлем мерзімі өтті",
                        "<p>Төлем мерзімі өтті.</p>");
        when(presets.resolve(
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        TemplateChannel.EMAIL,
                        "kk"))
                .thenReturn(resolved);

        TemplateVersion draft = mock(TemplateVersion.class);
        when(draft.getStatus()).thenReturn(TemplateVersionStatus.DRAFT);
        when(templates.createVersion(
                        tenantId,
                        createdTemplateId,
                        "kk",
                        TemplateChannel.EMAIL,
                        resolved.subject(),
                        resolved.content(),
                        null))
                .thenReturn(draft);

        AppliedTemplatePreset applied =
                service.apply(
                        tenantId,
                        null,
                        TemplatePresetCode.PAYMENT_OVERDUE,
                        TemplateChannel.EMAIL,
                        "kk");

        assertThat(applied.templateCreated()).isTrue();
        assertThat(applied.requestedLocale()).isEqualTo("kk");
        assertThat(applied.seedLocale()).isEqualTo("kk");
        verify(templates).create(tenantId, "PAYMENT_OVERDUE", "Payment Overdue", "PAYMENT");
        verify(templates)
                .createVersion(
                        tenantId,
                        createdTemplateId,
                        "kk",
                        TemplateChannel.EMAIL,
                        resolved.subject(),
                        resolved.content(),
                        isNull());
    }

    private TemplatePreset preset(TemplatePresetCode code) {
        return new TemplatePreset(
                code,
                "PAYMENT",
                List.of(TemplateChannel.EMAIL),
                Map.of(
                        "en",
                        new io.collectra.api.template.preset.TemplatePresetTranslation(
                                "subject", "text", "<p>html</p>")));
    }
}
