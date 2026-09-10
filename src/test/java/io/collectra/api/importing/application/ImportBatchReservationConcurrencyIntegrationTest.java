package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.template.domain.DocumentTemplate;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.infrastructure.DocumentTemplateRepository;
import io.collectra.api.template.infrastructure.TemplateVersionRepository;
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
    @Autowired SourceSchemaRepository schemas;
    @Autowired MappingProfileRepository profiles;
    @Autowired DocumentTemplateRepository templates;
    @Autowired TemplateVersionRepository versions;

    @Test
    void concurrentReservationsProduceExactlyOneBatchForTenantAndKey() throws Exception {
        Tenant tenant =
                tenants.saveAndFlush(new Tenant("import-race-" + UUID.randomUUID(), "Import Race"));
        References references = references(tenant);
        String key = "same-key-" + UUID.randomUUID();
        String requestHash = "a".repeat(64);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Object> reserve =
                () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("reservation race did not start");
                    }
                    try {
                        return reservations.reserve(
                                tenant.getId(),
                                key,
                                requestHash,
                                references.mappingProfileId(),
                                references.templateVersionId());
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

            assertThat(
                            results.stream()
                                    .filter(
                                            ImportBatchReservationService.Reservation.class
                                                    ::isInstance)
                                    .count())
                    .isEqualTo(1);
            assertThat(
                            results.stream()
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
        Tenant first =
                tenants.saveAndFlush(new Tenant("import-a-" + UUID.randomUUID(), "Import A"));
        Tenant second =
                tenants.saveAndFlush(new Tenant("import-b-" + UUID.randomUUID(), "Import B"));
        References firstReferences = references(first);
        References secondReferences = references(second);
        String key = "shared-key";
        String requestHash = "b".repeat(64);

        var firstReservation =
                reservations.reserve(
                        first.getId(),
                        key,
                        requestHash,
                        firstReferences.mappingProfileId(),
                        firstReferences.templateVersionId());
        var secondReservation =
                reservations.reserve(
                        second.getId(),
                        key,
                        requestHash,
                        secondReferences.mappingProfileId(),
                        secondReferences.templateVersionId());

        assertThat(firstReservation.batch().getId())
                .isNotEqualTo(secondReservation.batch().getId());
        assertThat(firstReservation.created()).isTrue();
        assertThat(secondReservation.created()).isTrue();
    }

    private References references(Tenant tenant) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SourceSchema schema =
                schemas.saveAndFlush(
                        new SourceSchema(
                                tenant.getId(),
                                "IMPORT_" + suffix,
                                "Import Schema",
                                SourceFormat.JSON,
                                1));
        MappingProfile profile =
                profiles.saveAndFlush(
                        new MappingProfile(
                                tenant.getId(),
                                schema.getId(),
                                "IMPORT_" + suffix,
                                "Import Profile",
                                "INVOICE",
                                1));
        DocumentTemplate template =
                templates.saveAndFlush(
                        new DocumentTemplate(
                                tenant.getId(), "IMPORT_" + suffix, "Import Template", "INVOICE"));
        TemplateVersion version =
                versions.saveAndFlush(
                        new TemplateVersion(template.getId(), 1, "en", "<p>import</p>", null));
        return new References(profile.getId(), version.getId());
    }

    private record References(UUID mappingProfileId, UUID templateVersionId) {}
}
