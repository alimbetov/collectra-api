package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.ImportBatch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImportBatchRepository
        extends JpaRepository<ImportBatch, UUID>, JpaSpecificationExecutor<ImportBatch> {
    Optional<ImportBatch> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<ImportBatch> findByIdAndTenantId(UUID id, UUID tenantId);
}
