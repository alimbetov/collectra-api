package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportOperationsPostgresIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired ImportBatchRepository batches;
    @Autowired SourceSchemaRepository schemas;
    @Autowired MappingProfileRepository profiles;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired ImportOperationsQueryService operations;

    @Test
    void historyAndDetailAreTenantScopedBoundedAndDeterministic() {
        Tenant a = tenants.saveAndFlush(new Tenant("ops-a-" + UUID.randomUUID(), "Ops A"));
        Tenant b = tenants.saveAndFlush(new Tenant("ops-b-" + UUID.randomUUID(), "Ops B"));
        UUID aProfile = profile(a);
        UUID bProfile = profile(b);
        UUID aTemplateVersion = templateVersion(a);
        UUID bTemplateVersion = templateVersion(b);
        ImportBatch first = batches.saveAndFlush(batch(a.getId(), "a", aProfile, aTemplateVersion));
        ImportBatch second =
                batches.saveAndFlush(batch(a.getId(), "b", aProfile, aTemplateVersion));
        batches.saveAndFlush(batch(b.getId(), "foreign", bProfile, bTemplateVersion));

        var page =
                operations.list(
                        a.getId(), "PROCESSING", null, null, null, 0, 100, List.of("id,asc"));
        assertThat(page.getContent())
                .extracting(ImportOperationsQueryService.BatchSummary::id)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        var detail = operations.get(a.getId(), first.getId());
        assertThat(detail.source()).isEqualTo("LEGACY_IMPORT");
        assertThat(detail.processingAttempts()).isEqualTo(1);

        assertThatThrownBy(() -> operations.get(b.getId(), first.getId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(
                        () ->
                                operations.list(
                                        a.getId(), "NOPE", null, null, null, 0, 50, List.of()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(
                        () -> operations.list(a.getId(), null, null, null, null, 0, 101, List.of()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(
                        () ->
                                operations.list(
                                        a.getId(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        0,
                                        50,
                                        List.of("updatedAt,desc")))
                .isInstanceOf(InvalidRequestException.class);
    }

    private UUID profile(Tenant tenant) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SourceSchema schema =
                schemas.saveAndFlush(
                        new SourceSchema(
                                tenant.getId(),
                                "OPS_" + suffix,
                                "Ops Schema",
                                SourceFormat.JSON,
                                1));
        MappingProfile profile =
                profiles.saveAndFlush(
                        new MappingProfile(
                                tenant.getId(),
                                schema.getId(),
                                "OPS_" + suffix,
                                "Ops Profile",
                                "INVOICE",
                                1));
        return profile.getId();
    }

    private UUID templateVersion(Tenant tenant) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "OPS_" + suffix, "Ops Template", "INVOICE"));
        TemplateVersion version =
                versions.saveAndFlush(
                        new TemplateVersion(template.getId(), 1, "en", "<p>ops</p>", null));
        return version.getId();
    }

    private ImportBatch batch(
            UUID tenantId, String key, UUID mappingProfileId, UUID templateVersionId) {
        return new ImportBatch(
                tenantId,
                "ops-" + key + "-" + UUID.randomUUID(),
                "b".repeat(64),
                mappingProfileId,
                templateVersionId);
    }
}
