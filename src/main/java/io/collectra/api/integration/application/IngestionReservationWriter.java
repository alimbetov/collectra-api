package io.collectra.api.integration.application;

import io.collectra.api.integration.domain.IngestionBatch;
import io.collectra.api.integration.infrastructure.IngestionBatchRepository;
import io.collectra.api.shared.outbox.OutboxService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionReservationWriter {
    private final IngestionBatchRepository batches;
    private final OutboxService outbox;
    private final Clock clock;

    public IngestionReservationWriter(IngestionBatchRepository batches, OutboxService outbox, Clock clock) {
        this.batches = batches; this.outbox = outbox; this.clock = clock;
    }

    @Transactional
    public IngestionBatch create(UUID id, UUID tenantId, UUID sourceId, UUID clientId,
            String sourceCode, String key, String hash, String requestId, UUID fileId,
            UUID schemaVersionId, UUID mappingVersionId, String contentType, String contextJson) {
        IngestionBatch batch = new IngestionBatch(id, tenantId, sourceId, clientId, sourceCode, key,
                hash, requestId, fileId, schemaVersionId, mappingVersionId, contentType,
                clock.instant(), contextJson);
        batches.saveAndFlush(batch);
        outbox.append(tenantId, "INGESTION", id, IngestionApplicationService.EVENT_TYPE,
                "{\"tenantId\":\"" + tenantId + "\",\"ingestionId\":\"" + id + "\"}");
        return batch;
    }
}
