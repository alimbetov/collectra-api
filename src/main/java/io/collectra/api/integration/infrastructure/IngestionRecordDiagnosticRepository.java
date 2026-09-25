package io.collectra.api.integration.infrastructure;

import io.collectra.api.integration.domain.IngestionRecordDiagnostic;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRecordDiagnosticRepository extends JpaRepository<IngestionRecordDiagnostic, UUID> {
    Optional<IngestionRecordDiagnostic> findByTenantIdAndBatchIdAndRecordOrder(UUID tenantId, UUID batchId, int order);
    Page<IngestionRecordDiagnostic> findAllByTenantIdAndBatchId(UUID tenantId, UUID batchId, Pageable pageable);
    Page<IngestionRecordDiagnostic> findAllByTenantIdAndBatchIdAndOutcome(UUID tenantId, UUID batchId, String outcome, Pageable pageable);
    Page<IngestionRecordDiagnostic> findAllByTenantIdAndBatchIdAndTargetType(UUID tenantId, UUID batchId, String type, Pageable pageable);
    Page<IngestionRecordDiagnostic> findAllByTenantIdAndBatchIdAndOutcomeAndTargetType(UUID tenantId, UUID batchId, String outcome, String type, Pageable pageable);
}
