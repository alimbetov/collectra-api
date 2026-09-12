package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageDeliveryRequestedTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void hasStableMinimalJsonContract() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageDeliveryRequested event = new MessageDeliveryRequested(tenantId, messageId);

        String serialized = json.writeValueAsString(event);
        MessageDeliveryRequested restored =
                json.readValue(serialized, MessageDeliveryRequested.class);

        assertThat(serialized)
                .isEqualTo(
                        "{\"tenantId\":\"" + tenantId + "\",\"messageId\":\"" + messageId + "\"}");
        assertThat(restored).isEqualTo(event);
    }

    @Test
    void requiresBothDurableIdentifiers() {
        UUID id = UUID.randomUUID();

        assertThatNullPointerException()
                .isThrownBy(() -> new MessageDeliveryRequested(null, id))
                .withMessage("tenantId");
        assertThatNullPointerException()
                .isThrownBy(() -> new MessageDeliveryRequested(id, null))
                .withMessage("messageId");
    }
}
