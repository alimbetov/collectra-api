package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ImportBatchReservationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired ImportBatchReservationService reservations;
    @Autowired ImportBatchRepository batches;
    @Autowired TenantRepository tenants;

    @Test
    void concurrentReservationsProduceExactlyOneBatchForTenantAndKey() throws Exception {
        Tenant tenant = tenants.saveAndFlush(
                new Tenant("import-race-" + UUID.randomUUID(), "Import Race"));
        String key = "same-key-" + UUID.randomUUID();
        String requestHash = "a".repeat(64);
        UUID mappingId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Object> reserve = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("reservation race did not start");
            }
            try {
                return reservations.reserve(
                        tenant.getId(), key, requestHash, mappingId, templateId);
            } catch (DataIntegrityViolationException conflict) {
                return conflict;
            }
        };

        try {
            Future<Object> first = executor.submit(reserve);
            Future<Object> second = executor.submit(reserve);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            results.add(first.get(10, TimeUnit.SECONDS));
            results.add(second.get(10, TimeUnit.SECONDS));

            assertThat(results.stream()
                            .filter(ImportBatchReservationService.Reservation.class::isInstance)
                            .count())
                    .isEqualTo(1);
            assertThat(results.stream()
                            .filter(DataIntegrityViolationException.class::isInstance)
                            .count())
                    .isEqualTo(1);
            assertThat(batches.findByTenantIdAndIdempotencyKey(tenant.getId(), key)).isPresent();
            assertThat(reservations.existing(tenant.getId(), key, requestHash).created()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameIdempotencyKeyIsIndependentAcrossTenants() {
        Tenant first = tenants.saveAndFlush(
                new Tenant("import-a-" + UUID.randomUUID(), "Import A"));
        Tenant second = tenants.saveAndFlush(
                new Tenant("import-b-" + UUID.randomUUID(), "Import B"));
        String key = "shared-key";
        String requestHash = "b".repeat(64);

        var firstReservation = reservations.reserve(
                first.getId(), key, requestHash, UUID.randomUUID(), UUID.randomUUID());
        var secondReservation = reservations.reserve(
                second.getId(), key, requestHash, UUID.randomUUID(), UUID.randomUUID());

        assertThat(firstReservation.batch().getId())
                .isNotEqualTo(secondReservation.batch().getId());
        assertThat(firstReservation.created()).isTrue();
        assertThat(secondReservation.created()).isTrue();
    }
}
