package io.collectra.api.integration.infrastructure;
import io.collectra.api.integration.domain.IngestionBatch;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface IngestionBatchRepository extends JpaRepository<IngestionBatch,UUID>{Optional<IngestionBatch> findByTenantIdAndIntegrationSourceIdAndIdempotencyKey(UUID tenantId,UUID sourceId,String key);Optional<IngestionBatch> findByIdAndTenantId(UUID id,UUID tenantId);}
