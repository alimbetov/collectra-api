package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportOperationsPostgresIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired ImportBatchRepository batches;
    @Autowired ImportOperationsQueryService operations;

    @Test
    void historyAndDetailAreTenantScopedBoundedAndDeterministic() {
        Tenant a =
                tenants.saveAndFlush(
                        new Tenant("ops-a-" + UUID.randomUUID(), "Ops A"));
        Tenant b =
                tenants.saveAndFlush(
                        new Tenant("ops-b-" + UUID.randomUUID(), "Ops B"));
        ImportBatch first = batches.saveAndFlush(batch(a.getId(), "a"));
        ImportBatch second = batches.saveAndFlush(batch(a.getId(), "b"));
        batches.saveAndFlush(batch(b.getId(), "foreign"));

        var page =
                operations.list(
                        a.getId(),
                        "PROCESSING",
                        null,
                        null,
                        null,
                        0,
                        100,
                        List.of("id,asc"));
        List<UUID> expected =
                List.of(first.getId(), second.getId()).stream()
                        .sorted(Comparator.naturalOrder())
                        .toList();
        assertThat(page.getContent())
                .extracting(ImportOperationsQueryService.BatchSummary::id)
                .containsExactlyElementsOf(expected);
        var detail = operations.get(a.getId(), first.getId());
        assertThat(detail.source()).isEqualTo("LEGACY_IMPORT");
        assertThat(detail.processingAttempts()).isEqualTo(1);

        assertThatThrownBy(() -> operations.get(b.getId(), first.getId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(
                        () ->
                                operations.list(
                                        a.getId(),
                                        "NOPE",
                                        null,
                                        null,
                                        null,
                                        0,
                                        50,
                                        List.of()))
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
                                        101,
                                        List.of()))
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

    private ImportBatch batch(UUID tenantId, String key) {
        return new ImportBatch(
                tenantId,
                "ops-" + key + "-" + UUID.randomUUID(),
                "b".repeat(64),
                UUID.randomUUID(),
                UUID.randomUUID());
    }
}
