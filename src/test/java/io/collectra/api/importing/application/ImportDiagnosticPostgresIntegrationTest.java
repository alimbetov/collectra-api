package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.importing.infrastructure.ImportRecordDiagnosticRepository;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
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

class ImportDiagnosticPostgresIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired ImportBatchRepository batches;
    @Autowired SourceSchemaRepository schemas;
    @Autowired MappingProfileRepository profiles;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;
    @Autowired ImportRecordDiagnosticRepository repository;
    @Autowired ImportDiagnosticService diagnostics;

    @Test
    void persistsMaskedDiagnosticsWithTenantIsolationStableOrderingAndBounds() {
        Tenant a = tenants.saveAndFlush(new Tenant("diag-a-" + UUID.randomUUID(), "Diag A"));
        Tenant b = tenants.saveAndFlush(new Tenant("diag-b-" + UUID.randomUUID(), "Diag B"));
        ImportBatch batch = batches.saveAndFlush(batch(a.getId(), profile(a), templateVersion(a)));

        diagnostics.persist(
                a.getId(),
                batch.getId(),
                2,
                2,
                "doc-2",
                "MAPPING",
                "z",
                "bad code",
                "email john.doe@example.com phone +7 701 123 45 67",
                "990101301234");
        diagnostics.persist(
                a.getId(),
                batch.getId(),
                1,
                1,
                "doc-1",
                "MAPPING",
                "b",
                "bad code",
                "invalid 990101301234",
                "john.doe@example.com");
        diagnostics.persist(
                a.getId(),
                batch.getId(),
                1,
                0,
                "doc-1",
                "MAPPING",
                "a",
                "bad code",
                "invalid",
                "+7 701 123 45 67");

        var page = diagnostics.errors(a.getId(), batch.getId(), 0, 100, List.of());
        assertThat(page.getContent())
                .extracting(ImportDiagnosticService.Diagnostic::recordNumber)
                .containsExactly(1, 1, 2);
        assertThat(page.getContent())
                .extracting(ImportDiagnosticService.Diagnostic::fieldPath)
                .containsExactly("a", "b", "z");
        assertThat(page.getContent())
                .allSatisfy(
                        d -> {
                            assertThat(d.safeDetail()).doesNotContain("990101301234");
                            assertThat(d.maskedSourceValue())
                                    .doesNotContain("john.doe@example.com", "701 123 45 67");
                        });
        assertThat(repository.count()).isEqualTo(3);

        assertThatThrownBy(() -> diagnostics.errors(b.getId(), batch.getId(), 0, 50, List.of()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> diagnostics.errors(a.getId(), batch.getId(), -1, 50, List.of()))
                .isInstanceOf(io.collectra.api.shared.error.InvalidRequestException.class);
        assertThatThrownBy(() -> diagnostics.errors(a.getId(), batch.getId(), 0, 101, List.of()))
                .isInstanceOf(io.collectra.api.shared.error.InvalidRequestException.class);
        assertThatThrownBy(
                        () ->
                                diagnostics.errors(
                                        a.getId(), batch.getId(), 0, 50, List.of("createdAt,desc")))
                .isInstanceOf(io.collectra.api.shared.error.InvalidRequestException.class);
    }

    private UUID profile(Tenant tenant) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SourceSchema schema =
                schemas.saveAndFlush(
                        new SourceSchema(
                                tenant.getId(),
                                "DIAG_" + suffix,
                                "Diag Schema",
                                SourceFormat.JSON,
                                1));
        MappingProfile profile =
                profiles.saveAndFlush(
                        new MappingProfile(
                                tenant.getId(),
                                schema.getId(),
                                "DIAG_" + suffix,
                                "Diag Profile",
                                "INVOICE",
                                1));
        return profile.getId();
    }

    private UUID templateVersion(Tenant tenant) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "DIAG_" + suffix, "Diag Template", "INVOICE"));
        TemplateVersion version =
                versions.saveAndFlush(
                        new TemplateVersion(template.getId(), 1, "en", "<p>diag</p>", null));
        return version.getId();
    }

    private ImportBatch batch(UUID tenantId, UUID mappingProfileId, UUID templateVersionId) {
        return new ImportBatch(
                tenantId,
                "diag-" + UUID.randomUUID(),
                "a".repeat(64),
                mappingProfileId,
                templateVersionId);
    }
}
