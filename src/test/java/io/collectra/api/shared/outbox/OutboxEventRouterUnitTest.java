package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.document.infrastructure.DocumentMessagingConfig;

import org.junit.jupiter.api.Test;

class OutboxEventRouterUnitTest {
    private final OutboxEventRouter router = new OutboxEventRouter();

    @Test
    void routesKnownDocumentEvent() {
        OutboxRoute route = router.route("DOCUMENT_GENERATION_REQUESTED");
        assertThat(route.exchange()).isEqualTo(DocumentMessagingConfig.EXCHANGE);
        assertThat(route.routingKey()).isEqualTo(DocumentMessagingConfig.ROUTING_KEY);
    }

    @Test
    void failsClosedForUnknownEvent() {
        assertThatThrownBy(() -> router.route("UNSUPPORTED_EVENT"))
                .isInstanceOf(UnknownOutboxEventTypeException.class);
    }
}
