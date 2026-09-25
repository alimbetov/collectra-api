package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.importing.infrastructure.ImportRecordDiagnosticRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportDiagnosticPostgresIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired ImportBatchRepository batches;
    @Autowired ImportRecordDiagnosticRepository repository;
    @Autowired ImportDiagnosticService diagnostics;

    @Test
    void persistsMaskedDiagnosticsWithTenantIsolationStableOrderingAndBounds() {
        Tenant a = tenants.saveAndFlush(new Tenant("diag-a-" + UUID.randomUUID(), "Diag A"));
        Tenant b = tenants.saveAndFlush(new Tenant("diag-b-" + UUID.randomUUID(), "Diag B"));
        ImportBatch batch = batches.saveAndFlush(batch(a.getId()));

        diagnostics.persist(a.getId(), batch.getId(), 2, 2, "doc-2", "MAPPING", "z", "bad code",
                "email john.doe@example.com phone +7 701 123 45 67", "990101301234");
        diagnostics.persist(a.getId(), batch.getId(), 1, 1, "doc-1", "MAPPING", "b", "bad code",
                "invalid 990101301234", "john.doe@example.com");
        diagnostics.persist(a.getId(), batch.getId(), 1, 0, "doc-1", "MAPPING", "a", "bad code",
                "invalid", "+7 701 123 45 67");

        var page = diagnostics.errors(a.getId(), batch.getId(), 0, 100, List.of());
        assertThat(page.getContent()).extracting(ImportDiagnosticService.Diagnostic::recordNumber)
                .containsExactly(1, 1, 2);
        assertThat(page.getContent()).extracting(ImportDiagnosticService.Diagnostic::fieldPath)
                .containsExactly("a", "b", "z");
        assertThat(page.getContent()).allSatisfy(d -> {
            assertThat(d.safeDetail()).doesNotContain("990101301234");
            assertThat(d.maskedSourceValue()).doesNotContain("john.doe@example.com", "701 123 45 67");
        });
        assertThat(repository.count()).isEqualTo(3);

        assertThatThrownBy(() -> diagnostics.errors(b.getId(), batch.getId(), 0, 50, List.of()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> diagnostics.errors(a.getId(), batch.getId(), -1, 50, List.of()))
                .isInstanceOf(io.collectra.api.shared.error.InvalidRequestException.class);
        assertThatThrownBy(() -> diagnostics.errors(a.getId(), batch.getId(), 0, 101, List.of()))
                .isInstanceOf(io.collectra.api.shared.error.InvalidRequestException.class);
        assertThatThrownBy(() -> diagnostics.errors(a.getId(), batch.getId(), 0, 50, List.of("createdAt,desc")))
                .isInstanceOf(io.collectra.api.shared.error.InvalidRequestException.class);
    }

    private ImportBatch batch(UUID tenantId) {
        return new ImportBatch(
                tenantId,
                "diag-" + UUID.randomUUID(),
                "a".repeat(64),
                UUID.randomUUID(),
                UUID.randomUUID());
    }
}
