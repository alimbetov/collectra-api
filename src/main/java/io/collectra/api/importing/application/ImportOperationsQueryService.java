package io.collectra.api.importing.application;

import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.domain.ImportBatchStatus;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportOperationsQueryService {
    public static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTS = Set.of("createdAt", "status", "id");
    private final ImportBatchRepository batches;

    public ImportOperationsQueryService(ImportBatchRepository batches) {
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public Page<BatchSummary> list(
            UUID tenantId,
            String status,
            Instant from,
            Instant to,
            String key,
            int page,
            int size,
            List<String> sort) {
        validatePage(page, size);
        ImportBatchStatus parsed = parseStatus(status);
        Sort ordering = parseSort(sort);
        Specification<ImportBatch> spec =
                (root, query, cb) -> {
                    List<Predicate> p = new ArrayList<>();
                    p.add(cb.equal(root.get("tenantId"), tenantId));
                    if (parsed != null) p.add(cb.equal(root.get("status"), parsed));
                    if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
                    if (to != null) p.add(cb.lessThan(root.get("createdAt"), to));
                    if (key != null && !key.isBlank())
                        p.add(cb.equal(root.get("idempotencyKey"), key.trim()));
                    return cb.and(p.toArray(Predicate[]::new));
                };
        return batches.findAll(spec, PageRequest.of(page, size, ordering)).map(this::summary);
    }

    @Transactional(readOnly = true)
    public BatchDetail get(UUID tenantId, UUID id) {
        return detail(
                batches.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Import batch not found")));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE)
            throw new InvalidRequestException(
                    "INVALID_PAGE_REQUEST", "page must be >= 0 and size must be between 1 and 100");
    }

    private ImportBatchStatus parseStatus(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return ImportBatchStatus.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_STATUS", "Unsupported import status: " + v);
        }
    }

    private Sort parseSort(List<String> values) {
        if (values == null || values.isEmpty())
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        List<Sort.Order> orders = new ArrayList<>();
        for (String value : values) {
            String[] parts = value.split(",", 2);
            String property = parts[0].trim();
            if (!SORTS.contains(property))
                throw new InvalidRequestException(
                        "UNSUPPORTED_SORT", "Unsupported import sort: " + property);
            Sort.Direction direction;
            try {
                direction =
                        parts.length == 1
                                ? Sort.Direction.ASC
                                : Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("UNSUPPORTED_SORT", "Unsupported sort direction");
            }
            orders.add(new Sort.Order(direction, property));
        }
        if (orders.stream().noneMatch(o -> o.getProperty().equals("id")))
            orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }

    private BatchSummary summary(ImportBatch b) {
        return new BatchSummary(
                b.getId(),
                b.getSource(),
                b.getStatus().name(),
                b.getIdempotencyKey(),
                b.getCreatedAt(),
                b.getProcessingStartedAt(),
                b.getCompletedAt(),
                b.getRecordCount(),
                b.getCreatedCount(),
                b.getReusedCount(),
                b.getConflictCount(),
                b.getFailedCount());
    }

    private BatchDetail detail(ImportBatch b) {
        return new BatchDetail(
                b.getId(),
                b.getSource(),
                b.getStatus().name(),
                b.getIdempotencyKey(),
                b.getRawSourceFileId(),
                b.getSourceSchemaVersionId(),
                b.getMappingProfileVersionId(),
                b.getTemplateVersionId(),
                b.getCreatedAt(),
                b.getProcessingStartedAt(),
                b.getCompletedAt(),
                b.getProcessingAttempts(),
                b.getRecordCount(),
                b.getCreatedCount(),
                b.getReusedCount(),
                b.getConflictCount(),
                b.getFailedCount(),
                b.getErrorCode(),
                b.getErrorMessage());
    }

    public record BatchSummary(
            UUID id,
            String source,
            String status,
            String idempotencyKey,
            Instant receivedAt,
            Instant processingStartedAt,
            Instant completedAt,
            int recordCount,
            int createdCount,
            int reusedCount,
            int conflictCount,
            int failedCount) {}

    public record BatchDetail(
            UUID id,
            String source,
            String status,
            String idempotencyKey,
            UUID rawSourceFileId,
            UUID sourceSchemaVersionId,
            UUID mappingProfileVersionId,
            UUID templateVersionId,
            Instant receivedAt,
            Instant processingStartedAt,
            Instant completedAt,
            int processingAttempts,
            int recordCount,
            int createdCount,
            int reusedCount,
            int conflictCount,
            int failedCount,
            String errorCode,
            String errorMessage) {}
}
