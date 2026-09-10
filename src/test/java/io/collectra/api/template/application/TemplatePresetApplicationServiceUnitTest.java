package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.localization.application.TenantLocaleService;
import io.collectra.api.localization.domain.TenantLocale;
import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.preset.TemplatePresetCode;
import io.collectra.api.template.preset.TemplatePresetContent;
import io.collectra.api.template.preset.TemplatePresetService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplatePresetApplicationServiceUnitTest {

    @Test
    void createsUzbekDraftFromRussianFallbackWithoutChangingTargetLocale() {
        TenantLocaleService tenantLocales = mock(TenantLocaleService.class);
        TemplatePresetService presets = mock(TemplatePresetService.class);
        TemplateManagementService templates = mock(TemplateManagementService.class);
        TemplatePresetApplicationService service =
                new TemplatePresetApplicationService(tenantLocales, presets, templates);

        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        TenantLocale uz = mock(TenantLocale.class);
        when(uz.getLocale()).thenReturn("uz");
        when(tenantLocales.requireEnabled(tenantId, "uz")).thenReturn(uz);

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
}
