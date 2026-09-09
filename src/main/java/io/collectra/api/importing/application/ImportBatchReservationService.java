package io.collectra.api.importing.application;

import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchReservationService {
    private final ImportBatchRepository batches;

    public ImportBatchReservationService(ImportBatchRepository batches) { this.batches = batches; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(UUID tenantId, String key, String requestHash,
            UUID mappingProfileVersionId, UUID templateVersionId) {
        var existing = batches.findByTenantIdAndIdempotencyKey(tenantId, key);
        if (existing.isPresent()) return existing(existing.get(), requestHash);
        ImportBatch created = batches.saveAndFlush(new ImportBatch(tenantId, key, requestHash,
                mappingProfileVersionId, templateVersionId));
        return new Reservation(created, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Reservation existing(UUID tenantId, String key, String requestHash) {
        ImportBatch batch = batches.findByTenantIdAndIdempotencyKey(tenantId, key)
                .orElseThrow(() -> new IllegalStateException("Concurrent import reservation disappeared"));
        return existing(batch, requestHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID tenantId, UUID batchId, String code, String message) {
        ImportBatch batch = get(tenantId, batchId);
        batch.failed(code, message);
    }

    @Transactional(readOnly = true)
    public ImportBatch get(UUID tenantId, UUID batchId) {
        return batches.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Import batch not found"));
    }

    private Reservation existing(ImportBatch batch, String requestHash) {
        if (!batch.getRequestHash().equals(requestHash))
            throw new IllegalArgumentException("Idempotency key was already used with another request");
        return new Reservation(batch, false);
    }

    public record Reservation(ImportBatch batch, boolean created) {}
}
