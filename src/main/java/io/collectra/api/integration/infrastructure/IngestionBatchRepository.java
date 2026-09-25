package io.collectra.api.integration.infrastructure;

import io.collectra.api.integration.domain.IngestionBatch;
import io.collectra.api.integration.domain.IngestionStatus;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, UUID>, JpaSpecificationExecutor<IngestionBatch> {
    Optional<IngestionBatch> findByTenantIdAndIntegrationSourceIdAndServiceClientIdAndIdempotencyKey(
            UUID tenantId, UUID sourceId, UUID clientId, String key);
    Optional<IngestionBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("select b.id from IngestionBatch b where b.status = 'RETRY_WAIT' and b.nextAttemptAt <= :now order by b.nextAttemptAt")
    List<UUID> findRetryable(@Param("now") Instant now, Pageable pageable);

    @Query("select b.id from IngestionBatch b where b.status = 'PROCESSING' and b.processingStartedAt < :before order by b.processingStartedAt")
    List<UUID> findStale(@Param("before") Instant before, Pageable pageable);
}
