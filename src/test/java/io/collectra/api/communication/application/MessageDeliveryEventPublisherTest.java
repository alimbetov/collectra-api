package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.shared.outbox.OutboxService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MessageDeliveryEventPublisherTest {
    @Test
    void appendsMinimalEventToSharedOutbox() throws Exception {
        OutboxService outbox = mock(OutboxService.class);
        ObjectMapper json = new ObjectMapper();
        MessageDeliveryEventPublisher publisher = new MessageDeliveryEventPublisher(outbox, json);
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        publisher.requestDelivery(tenantId, messageId);

        verify(outbox)
                .append(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(MessageDeliveryRequested.AGGREGATE_TYPE),
                        org.mockito.ArgumentMatchers.eq(messageId),
                        org.mockito.ArgumentMatchers.eq(MessageDeliveryRequested.EVENT_TYPE),
                        payload.capture());
        assertThat(json.readTree(payload.getValue()).path("tenantId").asText())
                .isEqualTo(tenantId.toString());
        assertThat(json.readTree(payload.getValue()).path("messageId").asText())
                .isEqualTo(messageId.toString());
    }
}
