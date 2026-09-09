package io.collectra.api.importing.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "import_batch_documents")
public class ImportBatchDocument {
    @Id private UUID id;
    @Column(name = "import_batch_id", nullable = false) private UUID importBatchId;
    @Column(name = "document_order", nullable = false) private int documentOrder;
    @Column(name = "document_key", nullable = false, length = 300) private String documentKey;
    @Column(name = "generation_job_id", nullable = false) private UUID generationJobId;
    protected ImportBatchDocument() {}
    public ImportBatchDocument(UUID batchId, int order, String key, UUID jobId) {
        this.id=UUID.randomUUID(); this.importBatchId=batchId; this.documentOrder=order;
        this.documentKey=key; this.generationJobId=jobId;
    }
    public UUID getId(){return id;} public int getDocumentOrder(){return documentOrder;}
    public String getDocumentKey(){return documentKey;} public UUID getGenerationJobId(){return generationJobId;}
}
