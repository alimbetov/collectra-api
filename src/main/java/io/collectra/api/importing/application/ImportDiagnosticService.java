package io.collectra.api.importing.application;

import io.collectra.api.importing.domain.ImportRecordDiagnostic;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import io.collectra.api.importing.infrastructure.ImportRecordDiagnosticRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportDiagnosticService {
    public static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SUPPORTED_SORT =
            Set.of("recordNumber", "fieldPath", "id");

    private final ImportBatchRepository batches;
    private final ImportRecordDiagnosticRepository diagnostics;
    private final ImportDiagnosticMaskingPolicy masking;
    private final Clock clock;

    public ImportDiagnosticService(
            ImportBatchRepository batches,
            ImportRecordDiagnosticRepository diagnostics,
            ImportDiagnosticMaskingPolicy masking,
            Clock clock) {
        this.batches = batches;
        this.diagnostics = diagnostics;
        this.masking = masking;
        this.clock = clock;
    }

    @Transactional
    public Diagnostic persist(
            UUID tenantId,
            UUID importId,
            int recordNumber,
            int recordOrder,
            String documentKey,
            String stage,
            String fieldPath,
            String errorCode,
            String safeDetail,
            String rawSourceValue) {
        requireImport(tenantId, importId);
        if (recordNumber < 0 || recordOrder < 0) {
            throw new InvalidRequestException(
                    "INVALID_IMPORT_DIAGNOSTIC", "recordNumber and recordOrder must be non-negative");
        }
        ImportRecordDiagnostic entity =
                new ImportRecordDiagnostic(
                        tenantId,
                        importId,
                        recordNumber,
                        recordOrder,
                        bound(documentKey, 300),
                        requiredBound(stage, 60, "stage"),
                        masking.fieldPath(fieldPath),
                        masking.errorCode(errorCode),
                        masking.safeDetail(safeDetail),
                        masking.maskedSourceValue(rawSourceValue),
                        Instant.now(clock));
        return toDto(diagnostics.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<Diagnostic> errors(UUID tenantId, UUID importId, int page, int size, List<String> sort) {
        requireImport(tenantId, importId);
        if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "INVALID_PAGE_REQUEST", "page must be >= 0 and size must be between 1 and 100");
        }
        validateSort(sort);
        Sort stable =
                Sort.by(
                        Sort.Order.asc("recordNumber"),
                        Sort.Order.asc("fieldPath").nullsFirst(),
                        Sort.Order.asc("id"));
        return diagnostics
                .findAllByTenantIdAndImportId(tenantId, importId, PageRequest.of(page, size, stable))
                .map(this::toDto);
    }

    private void requireImport(UUID tenantId, UUID importId) {
        if (batches.findByIdAndTenantId(importId, tenantId).isEmpty()) {
            throw new NoSuchElementException("Import batch not found");
        }
    }

    private void validateSort(List<String> sort) {
        if (sort == null || sort.isEmpty()) return;
        for (String value : sort) {
            String property = value == null ? "" : value.split(",", 2)[0].trim();
            if (!SUPPORTED_SORT.contains(property)) {
                throw new InvalidRequestException(
                        "UNSUPPORTED_SORT", "Unsupported import diagnostic sort: " + property);
            }
        }
    }

    private String requiredBound(String value, int max, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("INVALID_IMPORT_DIAGNOSTIC", name + " is required");
        }
        return bound(value.trim(), max);
    }

    private String bound(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private Diagnostic toDto(ImportRecordDiagnostic value) {
        return new Diagnostic(
                value.getId(),
                value.getRecordNumber(),
                value.getRecordOrder(),
                value.getDocumentKey(),
                value.getStage(),
                value.getFieldPath(),
                value.getErrorCode(),
                value.getSafeDetail(),
                value.getMaskedSourceValue(),
                value.getCreatedAt());
    }

    public record Diagnostic(
            UUID id,
            int recordNumber,
            int recordOrder,
            String documentKey,
            String stage,
            String fieldPath,
            String errorCode,
            String safeDetail,
            String maskedSourceValue,
            Instant createdAt) {}
}
