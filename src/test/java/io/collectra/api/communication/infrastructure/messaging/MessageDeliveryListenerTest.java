package io.collectra.api.communication.infrastructure.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.collectra.api.communication.application.MessageDeliveryRequested;
import io.collectra.api.communication.application.MessageDeliveryWorker;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageDeliveryListenerTest {
    @Test
    void delegatesDurableIdentifiersToWorker() {
        MessageDeliveryWorker worker = mock(MessageDeliveryWorker.class);
        MessageDeliveryListener listener = new MessageDeliveryListener(worker);
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        listener.consume(new MessageDeliveryRequested(tenantId, messageId));

        verify(worker).deliver(tenantId, messageId);
    }
}
