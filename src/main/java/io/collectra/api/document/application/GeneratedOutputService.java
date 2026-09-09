package io.collectra.api.document.application;

import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class GeneratedOutputService {
    private final GeneratedDocumentRepository documents;
    private final DocumentStorage storage;

    public GeneratedOutputService(GeneratedDocumentRepository documents, DocumentStorage storage) {
        this.documents = documents;
        this.storage = storage;
    }

    @Transactional
    public GeneratedDocument storeHtml(UUID tenantId, UUID jobId, String html) {
        return store(
                tenantId,
                jobId,
                OutputFormat.HTML,
                html.getBytes(StandardCharsets.UTF_8),
                "text/html; charset=UTF-8");
    }

    @Transactional
    public GeneratedDocument storePdf(UUID tenantId, UUID jobId, byte[] pdf) {
        return store(tenantId, jobId, OutputFormat.PDF, pdf, "application/pdf");
    }

    protected GeneratedDocument store(
            UUID tenantId, UUID jobId, OutputFormat format, byte[] content, String mediaType) {
        return documents
                .findByGenerationJobIdAndFormat(jobId, format)
                .orElseGet(
                        () -> {
                            String key =
                                    tenantId
                                            + "/generation-jobs/"
                                            + jobId
                                            + "/document."
                                            + format.name().toLowerCase(java.util.Locale.ROOT);
                            var stored = storage.put(key, content, mediaType);
                            return documents.save(
                                    new GeneratedDocument(
                                            tenantId,
                                            jobId,
                                            format,
                                            stored.key(),
                                            stored.mediaType(),
                                            stored.sizeBytes(),
                                            stored.sha256()));
                        });
    }

    public byte[] read(GeneratedDocument document) {
        return storage.get(document.getStorageKey());
    }
}
