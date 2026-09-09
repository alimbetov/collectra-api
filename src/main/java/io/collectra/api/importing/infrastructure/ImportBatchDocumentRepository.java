package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.ImportBatchDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchDocumentRepository extends JpaRepository<ImportBatchDocument, UUID> {
    List<ImportBatchDocument> findAllByImportBatchIdOrderByDocumentOrder(UUID importBatchId);
}
