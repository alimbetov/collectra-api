package io.collectra.api.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateChannel;
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
    private final ObjectMapper json = new ObjectMapper();

    private TemplateManagementService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new TemplateManagementService(templates, versions, fields, compiler, renderer);
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
        FieldDefinition customerName = field("customer.name");
        FieldDefinition customErpCode = field("custom.erp.code");
        when(versions.findByIdAndTenantId(version.getId(), tenantId))
                .thenReturn(Optional.of(version));
        when(fields.findAvailable(tenantId)).thenReturn(List.of(customerName, customErpCode));

        var result = service.validate(tenantId, version.getId());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.VALIDATED);
    }

    @Test
    void persistsBuilderJsonTogetherWithDerivedHtml() throws Exception {
        UUID templateId = UUID.randomUUID();
        DocumentTemplate template = new DocumentTemplate(tenantId, "INVOICE", "Invoice", "INVOICE");
        when(templates.findByIdAndTenantId(templateId, tenantId)).thenReturn(Optional.of(template));
        when(versions.findAllByTemplateIdAndLocaleAndChannelOrderByTemplateVersionDesc(
                        templateId, "ru", TemplateChannel.PDF))
                .thenReturn(List.of());
        when(versions.save(org.mockito.ArgumentMatchers.any(TemplateVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var builderJson =
                json.readTree(
                        """
                        {"version":"1.0","blocks":[
                          {"type":"richText","props":{"content":[
                            {"type":"placeholder","key":"customer.name"}
                          ]}}
                        ]}
                        """);

        TemplateVersion version =
                service.createBuilderVersion(
                        tenantId,
                        templateId,
                        "ru",
                        TemplateChannel.PDF,
                        null,
                        builderJson,
                        "<div>{{customer.name}}</div>",
                        null);

        assertThat(version.isBuilderManaged()).isTrue();
        assertThat(version.getBuilderJson()).isEqualTo(builderJson);
        assertThat(version.getContentHtml()).contains("{{customer.name}}");
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
        FieldDefinition customerName = field("customer.name");
        when(versions.findByIdAndTenantId(version.getId(), tenantId))
                .thenReturn(Optional.of(version));
        when(fields.findAvailable(tenantId)).thenReturn(List.of(customerName));

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
        verifyNoInteractions(fields);
    }

    private FieldDefinition field(String key) {
        FieldDefinition field = mock(FieldDefinition.class);
        when(field.getKey()).thenReturn(key);
        return field;
    }
}
