package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.ImportBatch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    Optional<ImportBatch> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    Optional<ImportBatch> findByIdAndTenantId(UUID id, UUID tenantId);
}
