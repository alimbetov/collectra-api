package io.collectra.api.importing.application;

import io.collectra.api.document.application.GenerationJobService;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.importing.domain.ImportBatch;
import io.collectra.api.importing.domain.ImportBatchDocument;
import io.collectra.api.importing.domain.ImportBatchStatus;
import io.collectra.api.importing.infrastructure.ImportBatchDocumentRepository;
import io.collectra.api.importing.infrastructure.ImportBatchRepository;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchProcessingService {
    private final ImportBatchRepository batches;
    private final ImportBatchDocumentRepository documents;
    private final MappingExecutionService mappings;
    private final GenerationJobService jobs;

    public ImportBatchProcessingService(ImportBatchRepository batches,
            ImportBatchDocumentRepository documents, MappingExecutionService mappings,
            GenerationJobService jobs) {
        this.batches = batches; this.documents = documents; this.mappings = mappings; this.jobs = jobs;
    }

    @Transactional
    public void process(UUID tenantId, UUID batchId, UUID mappingProfileVersionId,
            UUID templateVersionId, byte[] input, Set<OutputFormat> formats) {
        ImportBatch batch = batches.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Import batch not found"));
        if (batch.getStatus() != ImportBatchStatus.PROCESSING)
            throw new IllegalStateException("Import batch is not processable");
        var mapped = mappings.executeBatch(tenantId, mappingProfileVersionId, input);
        for (var document : mapped.documents()) {
            var single = new MappingExecutionService.MappingResult(mapped.mappingProfileId(),
                    mapped.sourceSchemaVersionId(), mapped.documentType(),
                    document.normalizedPayload(), mapped.mappingConfigSha256());
            var job = jobs.createMapped(tenantId, mappingProfileVersionId, templateVersionId,
                    single, formats);
            documents.save(new ImportBatchDocument(batch.getId(), document.order(),
                    document.documentKey(), job.getId()));
        }
        batch.accepted(mapped.documents().size());
    }
}
