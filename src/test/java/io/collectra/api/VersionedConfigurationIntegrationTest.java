package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.importing.application.MappingProfileManagementService;
import io.collectra.api.importing.application.SourceSchemaManagementService;
import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.template.application.TemplateManagementService;
import io.collectra.api.template.domain.FieldDataType;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.domain.TemplateVersionStatus;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VersionedConfigurationIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired FieldDefinitionRepository fields;
    @Autowired SourceSchemaManagementService schemas;
    @Autowired MappingProfileManagementService mappings;
    @Autowired TemplateManagementService templates;
    @Autowired ObjectMapper json;

    @Test
    void versionsConfigurationTestsMappingAndLocksPublishedVersions() {
        Tenant tenant = tenants.save(new Tenant("versioned-" + UUID.randomUUID(), "Versioned"));
        FieldDefinition target = fields.save(new FieldDefinition(tenant.getId(),
                "custom.config.customer", "Customer", FieldDataType.STRING, "INVOICE",
                false, true, json.createObjectNode()));

        var schemaDefinition = schemas.create(tenant.getId(), "INVOICE_CSV", "Invoice CSV");
        var schemaV1 = schemas.createVersion(tenant.getId(), schemaDefinition.getId(), SourceFormat.CSV);
        var source = schemas.addField(tenant.getId(), schemaV1.getId(), "Customer", "STRING",
                "Acme", true, 1);
        assertThat(schemas.validate(tenant.getId(), schemaV1.getId()).valid()).isTrue();
        assertThat(schemas.publish(tenant.getId(), schemaV1.getId()).getStatus())
                .isEqualTo(DefinitionStatus.PUBLISHED);
        assertThatThrownBy(() -> schemas.addField(tenant.getId(), schemaV1.getId(), "Other",
                "STRING", null, false, 2)).isInstanceOf(IllegalArgumentException.class);

        var mappingDefinition = mappings.create(tenant.getId(), "INVOICE_MAP", "Invoice map", "INVOICE");
        var mappingV1 = mappings.createVersion(tenant.getId(), mappingDefinition.getId(), schemaV1.getId());
        mappings.addRule(tenant.getId(), mappingV1.getId(), source.getId(), target.getId(),
                json.createObjectNode().put("type", "TRIM"), null, true);
        assertThat(mappings.validate(tenant.getId(), mappingV1.getId()).valid()).isTrue();
        var testResult = mappings.test(tenant.getId(), mappingV1.getId(),
                "Customer\n  Acme  \n".getBytes(StandardCharsets.UTF_8));
        assertThat(testResult.normalizedPayload().at("/custom/config/customer").asText())
                .isEqualTo("Acme");
        assertThat(mappings.publish(tenant.getId(), mappingV1.getId()).getStatus())
                .isEqualTo(DefinitionStatus.PUBLISHED);

        var template = templates.create(tenant.getId(), "INVOICE", "Invoice", "INVOICE");
        var templateV1 = templates.createVersion(tenant.getId(), template.getId(), "en",
                "<p>{{custom.config.customer}}</p>", null);
        assertThat(templates.validate(tenant.getId(), templateV1.getId()).valid()).isTrue();
        assertThat(templates.publish(tenant.getId(), templateV1.getId()).getStatus())
                .isEqualTo(TemplateVersionStatus.PUBLISHED);
        assertThatThrownBy(() -> templates.update(tenant.getId(), templateV1.getId(),
                "<p>changed</p>", null)).isInstanceOf(IllegalStateException.class);

        var schemaV2 = schemas.createVersion(tenant.getId(), schemaDefinition.getId(), SourceFormat.CSV);
        assertThat(schemaV2.getSchemaVersion()).isEqualTo(2);
        assertThat(schemas.fields(tenant.getId(), schemaV2.getId())).hasSize(1);
    }
}
