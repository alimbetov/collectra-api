package io.collectra.api.integration.application;

import io.collectra.api.integration.domain.*;
import io.collectra.api.integration.infrastructure.*;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionQueryService {
    private final IngestionBatchRepository batches;
    private final IngestionRecordDiagnosticRepository diagnostics;
    public IngestionQueryService(IngestionBatchRepository batches, IngestionRecordDiagnosticRepository diagnostics) {
        this.batches = batches; this.diagnostics = diagnostics;
    }

    @Transactional(readOnly = true)
    public Page<BatchSummary> list(UUID tenantId, String status, Instant from, Instant to,
            String sourceCode, String idempotencyKey, Pageable pageable) {
        Specification<IngestionBatch> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>(); p.add(cb.equal(root.get("tenantId"), tenantId));
            if (status != null) p.add(cb.equal(root.get("status"), IngestionStatus.valueOf(status.toUpperCase(Locale.ROOT))));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("receivedAt"), to));
            if (sourceCode != null && !sourceCode.isBlank()) p.add(cb.equal(root.get("sourceCode"), sourceCode));
            if (idempotencyKey != null && !idempotencyKey.isBlank()) p.add(cb.equal(root.get("idempotencyKey"), idempotencyKey));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return batches.findAll(spec, pageable).map(this::summary);
    }

    @Transactional(readOnly = true)
    public BatchDetail get(UUID tenantId, UUID id) {
        IngestionBatch b = batches.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingestion batch not found"));
        return detail(b);
    }

    @Transactional(readOnly = true)
    public Page<RecordDiagnostic> records(UUID tenantId, UUID id, String outcome, String targetType, Pageable pageable) {
        if (batches.findByIdAndTenantId(id, tenantId).isEmpty())
            throw new NoSuchElementException("Ingestion batch not found");
        Page<IngestionRecordDiagnostic> page;
        if (text(outcome) && text(targetType)) page = diagnostics.findAllByTenantIdAndBatchIdAndOutcomeAndTargetType(tenantId,id,outcome.toUpperCase(Locale.ROOT),targetType.toUpperCase(Locale.ROOT),pageable);
        else if (text(outcome)) page = diagnostics.findAllByTenantIdAndBatchIdAndOutcome(tenantId,id,outcome.toUpperCase(Locale.ROOT),pageable);
        else if (text(targetType)) page = diagnostics.findAllByTenantIdAndBatchIdAndTargetType(tenantId,id,targetType.toUpperCase(Locale.ROOT),pageable);
        else page = diagnostics.findAllByTenantIdAndBatchId(tenantId,id,pageable);
        return page.map(this::diagnostic);
    }

    private boolean text(String v){return v!=null&&!v.isBlank();}
    private BatchSummary summary(IngestionBatch b){return new BatchSummary(b.getId(),b.getSourceCode(),b.getStatus().name(),b.getIdempotencyKey(),b.getRequestId(),b.getReceivedAt(),b.getCompletedAt(),b.getRecordCount(),b.getCreatedCount(),b.getReusedCount(),b.getConflictCount(),b.getFailedCount());}
    private BatchDetail detail(IngestionBatch b){return new BatchDetail(b.getId(),b.getSourceCode(),b.getStatus().name(),b.getIdempotencyKey(),b.getRequestId(),b.getRawSourceFileId(),b.getSourceSchemaVersionId(),b.getMappingProfileVersionId(),b.getReceivedAt(),b.getProcessingStartedAt(),b.getCompletedAt(),b.getProcessingAttempts(),b.getRecordCount(),b.getCreatedCount(),b.getReusedCount(),b.getConflictCount(),b.getFailedCount(),b.getErrorCode(),b.getSafeErrorMessage());}
    private RecordDiagnostic diagnostic(IngestionRecordDiagnostic d){return new RecordDiagnostic(d.getId(),d.getRecordOrder(),d.getDocumentKey(),d.getOutcome(),d.getStage(),d.getTargetType(),d.getTargetId(),d.getExternalId(),d.getErrorCode(),d.getSafeErrorMessage(),d.getFieldPath(),d.getCreatedAt());}

    public record BatchSummary(UUID id,String sourceCode,String status,String idempotencyKey,String requestId,Instant receivedAt,Instant completedAt,int recordCount,int createdCount,int reusedCount,int conflictCount,int failedCount){}
    public record BatchDetail(UUID id,String sourceCode,String status,String idempotencyKey,String requestId,UUID rawSourceFileId,UUID sourceSchemaVersionId,UUID mappingProfileVersionId,Instant receivedAt,Instant processingStartedAt,Instant completedAt,int processingAttempts,int recordCount,int createdCount,int reusedCount,int conflictCount,int failedCount,String errorCode,String errorMessage){}
    public record RecordDiagnostic(UUID id,int recordOrder,String documentKey,String outcome,String stage,String targetType,UUID targetId,String externalId,String errorCode,String errorMessage,String fieldPath,Instant createdAt){}
}
