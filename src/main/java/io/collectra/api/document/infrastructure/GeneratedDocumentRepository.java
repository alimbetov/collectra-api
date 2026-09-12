package io.collectra.api.document.infrastructure;

import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.OutputFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {
    List<GeneratedDocument> findAllByGenerationJobIdOrderByFormat(UUID generationJobId);

    Optional<GeneratedDocument> findByGenerationJobIdAndFormat(
            UUID generationJobId, OutputFormat format);

    Optional<GeneratedDocument> findByTenantIdAndGenerationJobIdAndFormat(
            UUID tenantId, UUID generationJobId, OutputFormat format);

    Optional<GeneratedDocument> findByIdAndTenantId(UUID id, UUID tenantId);
}
