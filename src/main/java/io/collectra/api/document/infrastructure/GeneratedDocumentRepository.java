package io.collectra.api.document.infrastructure;

import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.OutputFormat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {
    List<GeneratedDocument> findAllByGenerationJobIdOrderByFormat(UUID generationJobId);

    Optional<GeneratedDocument> findByGenerationJobIdAndFormat(
            UUID generationJobId, OutputFormat format);
}
