package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateManagementServiceUnitTest {
    private final DocumentTemplateRepository templates = mock(DocumentTemplateRepository.class);
    private final TemplateVersionRepository versions = mock(TemplateVersionRepository.class);
    private final FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
    private final HtmlTemplatePolicy policy = new HtmlTemplatePolicy();
    private final TemplateCompiler compiler = new TemplateCompiler(policy, new PlaceholderScanner());
    private final TemplateRenderer renderer = new TemplateRenderer(compiler);

    private TemplateManagementService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service =
                new TemplateManagementService(
                        templates, versions, fields, policy, compiler, renderer);
        tenantId = UUID.randomUUID();
    }

    @Test
    void validatesTemplateUsingSameCanonicalPlaceholderGrammarAsRenderer() {
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-kz",
                        "<p>{{customer.name}}</p><b>{{custom.erp.code}}</b>",
                        null);
        when(versions.findByIdAndTenantId(version.getId(), tenantId))
                .thenReturn(Optional.of(version));
        when(fields.findAvailable(tenantId))
                .thenReturn(List.of(field("customer.name"), field("custom.erp.code")));

        var result = service.validate(tenantId, version.getId());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.VALIDATED);
    }

    @Test
    void rejectsUnknownPlaceholderWithoutChangingDraftStatus() {
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-kz",
                        "<p>{{customer.unknown}}</p>",
                        null);
        when(versions.findByIdAndTenantId(version.getId(), tenantId))
                .thenReturn(Optional.of(version));
        when(fields.findAvailable(tenantId)).thenReturn(List.of(field("customer.name")));

        var result = service.validate(tenantId, version.getId());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(
                        error ->
                                "UNKNOWN_PLACEHOLDER".equals(error.code())
                                        && error.message().contains("customer.unknown"));
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.DRAFT);
    }

    @Test
    void malformedPlaceholderFailsBeforeFieldCatalogLookup() {
        TemplateVersion version =
                new TemplateVersion(
                        UUID.randomUUID(),
                        1,
                        "ru-kz",
                        "<p>{{Customer.Name}}</p>",
                        null);
        when(versions.findByIdAndTenantId(version.getId(), tenantId))
                .thenReturn(Optional.of(version));

        var result = service.validate(tenantId, version.getId());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(
                        error ->
                                "INVALID_TEMPLATE".equals(error.code())
                                        && error.message().contains("canonical lowercase"));
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.DRAFT);
    }

    private FieldDefinition field(String key) {
        FieldDefinition field = mock(FieldDefinition.class);
        when(field.getKey()).thenReturn(key);
        return field;
    }
}
