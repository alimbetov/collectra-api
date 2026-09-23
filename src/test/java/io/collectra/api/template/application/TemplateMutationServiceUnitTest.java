package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.shared.error.BusinessConflictException;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplateMutationServiceUnitTest {
    private final DocumentTemplateRepository templates = mock(DocumentTemplateRepository.class);
    private final TemplateVersionRepository versions = mock(TemplateVersionRepository.class);
    private final TemplateManagementService management = mock(TemplateManagementService.class);
    private final TemplateMutationService mutations =
            new TemplateMutationService(templates, versions, management);

    @Test
    void rejectsStaleTemplateRevisionBeforeMutation() {
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        DocumentTemplate template =
                new DocumentTemplate(tenantId, "NOTICE", "Notice", "NOTIFICATION");
        when(templates.findByIdAndTenantId(templateId, tenantId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> mutations.rename(tenantId, templateId, "Updated", 7))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(
                        error ->
                                org.assertj.core.api.Assertions.assertThat(
                                                ((BusinessConflictException) error).getCode())
                                        .isEqualTo("VERSION_CONFLICT"));

        verify(management, never()).rename(tenantId, templateId, "Updated");
    }

    @Test
    void delegatesVersionMutationWhenRevisionMatches() {
        UUID tenantId = UUID.randomUUID();
        TemplateVersion version =
                new TemplateVersion(UUID.randomUUID(), 1, "ru", "<p>{{customer.name}}</p>", null);
        when(versions.findByIdAndTenantId(version.getId(), tenantId))
                .thenReturn(Optional.of(version));

        mutations.publish(tenantId, version.getId(), version.getVersion());

        verify(management).publish(tenantId, version.getId());
    }
}
