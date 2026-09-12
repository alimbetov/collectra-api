package io.collectra.api.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxServiceUnitTest {
    @Test
    void suppliesApplicationClockTimeToNewEvent() {
        Instant now = Instant.parse("2026-09-12T01:00:00Z");
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxService service = new OutboxService(repository, Clock.fixed(now, ZoneOffset.UTC));
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);

        service.append(null, "TEST", UUID.randomUUID(), "TEST_EVENT", "{}");

        verify(repository).save(event.capture());
        assertThat(event.getValue().getNextAttemptAt()).isEqualTo(now);
    }
}
