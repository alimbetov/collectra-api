package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.template.application.TemplateFrontendQueryService;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TemplateFrontendDetailIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateFrontendQueryService queries;

    @Test
    void directTemplateDetailIsTenantScopedAndExposesRevisionMetadata() {
        Tenant tenant =
                tenants.saveAndFlush(
                        new Tenant("template-detail-" + UUID.randomUUID(), "Template Detail"));
        Tenant other =
                tenants.saveAndFlush(
                        new Tenant("template-detail-other-" + UUID.randomUUID(), "Other Tenant"));

        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(),
                                "NOTICE_" + UUID.randomUUID().toString().replace("-", ""),
                                "Notice",
                                "NOTIFICATION"));

        var detail = queries.template(tenant.getId(), template.getId());

        assertThat(detail.id()).isEqualTo(template.getId());
        assertThat(detail.code()).isEqualTo(template.getCode());
        assertThat(detail.name()).isEqualTo("Notice");
        assertThat(detail.documentType()).isEqualTo("NOTIFICATION");
        assertThat(detail.status()).isEqualTo("ACTIVE");
        assertThat(detail.createdAt()).isNotNull();
        assertThat(detail.updatedAt()).isNotNull();
        assertThat(detail.revision()).isEqualTo(template.getVersion());

        assertThatThrownBy(() -> queries.template(other.getId(), template.getId()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Template not found");
    }
}
