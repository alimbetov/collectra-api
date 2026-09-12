package io.collectra.api.document.application;

import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeneratedOutputService {
    private final GeneratedDocumentRepository documents;
    private final DocumentStorage storage;
    private final Clock clock;

    public GeneratedOutputService(
            GeneratedDocumentRepository documents, DocumentStorage storage, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
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
                .findByTenantIdAndGenerationJobIdAndFormat(tenantId, jobId, format)
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
                                            stored.sha256(),
                                            clock.instant()));
                        });
    }

    public byte[] read(GeneratedDocument document) {
        return storage.get(document.getStorageKey());
    }
}
