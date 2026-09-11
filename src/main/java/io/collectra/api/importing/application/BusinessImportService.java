package io.collectra.api.importing.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BusinessImportService {
    private final MappingExecutionService mappings;
    private final BusinessRecordPersistenceService persistence;

    public BusinessImportService(
            MappingExecutionService mappings, BusinessRecordPersistenceService persistence) {
        this.mappings = mappings;
        this.persistence = persistence;
    }

    public ImportResult importRecords(UUID tenantId, UUID mappingProfileVersionId, byte[] content) {
        var mapped = mappings.executeBatch(tenantId, mappingProfileVersionId, content);
        List<RowResult> rows = new ArrayList<>();
        int created = 0;
        int reused = 0;
        int failed = 0;

        for (var document : mapped.documents()) {
            try {
                var result =
                        persistence.persist(
                                tenantId, mapped.documentType(), document.normalizedPayload());
                rows.add(
                        new RowResult(
                                document.order(),
                                document.documentKey(),
                                result.type(),
                                result.externalId(),
                                result.id(),
                                result.created() ? "CREATED" : "REUSED",
                                null));
                if (result.created()) {
                    created++;
                } else {
                    reused++;
                }
            } catch (RuntimeException ex) {
                failed++;
                rows.add(
                        new RowResult(
                                document.order(),
                                document.documentKey(),
                                mapped.documentType(),
                                null,
                                null,
                                "FAILED",
                                ex.getMessage()));
            }
        }

        return new ImportResult(mapped.documents().size(), created, reused, failed, rows);
    }

    public record ImportResult(
            int total, int created, int reused, int failed, List<RowResult> rows) {}

    public record RowResult(
            int order,
            String documentKey,
            String type,
            String externalId,
            UUID id,
            String status,
            String error) {}
}
