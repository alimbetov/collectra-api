package io.collectra.api.integration.application;

import io.collectra.api.shared.outbox.OutboxService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionRequestPublisher {
    private final OutboxService outbox;

    public IngestionRequestPublisher(OutboxService outbox) {
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requested(UUID tenantId, UUID ingestionId) {
        outbox.append(
                tenantId,
                "INGESTION",
                ingestionId,
                IngestionApplicationService.EVENT_TYPE,
                "{"tenantId":"" + tenantId + "","ingestionId":"" + ingestionId + ""}");
    }
}
