package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.infrastructure.MessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    @Test
    void rejectsInvalidConfiguration() {
        MessageRepository messages = mock(MessageRepository.class);
        MessageStateService states = mock(MessageStateService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThatThrownBy(
                        () ->
                                new MessageRecoveryService(
                                        messages, states, clock, Duration.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new MessageRecoveryService(
                                        messages, states, clock, Duration.ofMinutes(-1), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new MessageRecoveryService(
                                        messages, states, clock, Duration.ofMinutes(5), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesDeterministicCutoffAndCountsOnlySuccessfulRecoveries() {
        MessageRepository messages = mock(MessageRepository.class);
        MessageStateService states = mock(MessageStateService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MessageRecoveryService service =
                new MessageRecoveryService(messages, states, clock, Duration.ofMinutes(5), 2);
        Message first = queued(UUID.randomUUID());
        Message second = queued(UUID.randomUUID());
        Instant cutoff = NOW.minus(Duration.ofMinutes(5));

        when(messages.findStaleProcessingCandidates(cutoff, 2)).thenReturn(List.of(first, second));
        when(states.recoverStale(first.getTenantId(), first.getId(), cutoff, NOW)).thenReturn(true);
        when(states.recoverStale(second.getTenantId(), second.getId(), cutoff, NOW))
                .thenReturn(false);

        assertThat(service.recoverStale()).isOne();
        verify(messages).findStaleProcessingCandidates(cutoff, 2);
        verify(states).recoverStale(first.getTenantId(), first.getId(), cutoff, NOW);
        verify(states).recoverStale(second.getTenantId(), second.getId(), cutoff, NOW);
    }

    @Test
    void emptyBatchIsNoOp() {
        MessageRepository messages = mock(MessageRepository.class);
        MessageStateService states = mock(MessageStateService.class);
        MessageRecoveryService service =
                new MessageRecoveryService(
                        messages,
                        states,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofMinutes(5),
                        100);
        Instant cutoff = NOW.minus(Duration.ofMinutes(5));
        when(messages.findStaleProcessingCandidates(cutoff, 100)).thenReturn(List.of());

        assertThat(service.recoverStale()).isZero();
    }

    private Message queued(UUID tenantId) {
        return Message.queued(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                CommunicationChannel.EMAIL,
                "user@example.test",
                "ru-KZ",
                "Reminder",
                "body");
    }
}
