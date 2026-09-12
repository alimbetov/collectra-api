package io.collectra.api.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageDeliveryAttemptTest {
    private static final Instant STARTED = Instant.parse("2026-09-13T00:00:00Z");
    private static final Instant COMPLETED = STARTED.plusSeconds(5);

    @Test
    void startsWithStableIdentityAndStartedStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageDeliveryAttempt attempt =
                MessageDeliveryAttempt.started(tenantId, messageId, 1, "msg-key", STARTED);

        assertThat(attempt.getId()).isNotNull();
        assertThat(attempt.getTenantId()).isEqualTo(tenantId);
        assertThat(attempt.getMessageId()).isEqualTo(messageId);
        assertThat(attempt.getAttemptNo()).isEqualTo(1);
        assertThat(attempt.getDeliveryKey()).isEqualTo("msg-key");
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.STARTED);
        assertThat(attempt.getCompletedAt()).isNull();
    }

    @Test
    void acceptedAttemptIsTerminal() {
        MessageDeliveryAttempt attempt = attempt();
        attempt.accepted(" provider-42 ", COMPLETED);

        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.ACCEPTED);
        assertThat(attempt.getProviderReference()).isEqualTo("provider-42");
        assertThat(attempt.getCompletedAt()).isEqualTo(COMPLETED);
        assertThatThrownBy(() -> attempt.unknown("TIMEOUT", COMPLETED.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ambiguousAttemptCanOnlyBeReconciledToAccepted() {
        MessageDeliveryAttempt attempt = attempt();
        attempt.unknown("PROVIDER_TIMEOUT", COMPLETED);

        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.UNKNOWN);
        assertThat(attempt.getErrorCode()).isEqualTo("PROVIDER_TIMEOUT");

        attempt.acceptedAfterUnknown("provider-42", COMPLETED.plusSeconds(1));

        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.ACCEPTED);
        assertThat(attempt.getProviderReference()).isEqualTo("provider-42");
        assertThat(attempt.getErrorCode()).isNull();
    }

    @Test
    void nonUnknownAttemptCannotUseLateAcceptanceReconciliation() {
        assertThatThrownBy(() -> attempt().acceptedAfterUnknown("provider-42", COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void failureKindsArePersistedSeparately() {
        MessageDeliveryAttempt retryable = attempt();
        retryable.retryableFailure("TEMP", COMPLETED);
        assertThat(retryable.getStatus()).isEqualTo(DeliveryAttemptStatus.RETRYABLE_FAILURE);

        MessageDeliveryAttempt permanent = attempt();
        permanent.permanentFailure("PERM", COMPLETED);
        assertThat(permanent.getStatus()).isEqualTo(DeliveryAttemptStatus.PERMANENT_FAILURE);
    }

    @Test
    void invalidAttemptOrdinalAndKeyFailClosed() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                MessageDeliveryAttempt.started(
                                        tenantId, messageId, 0, "msg-key", STARTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptNo");
        assertThatThrownBy(
                        () ->
                                MessageDeliveryAttempt.started(
                                        tenantId, messageId, 1, " ", STARTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deliveryKey");
    }

    private MessageDeliveryAttempt attempt() {
        return MessageDeliveryAttempt.started(
                UUID.randomUUID(), UUID.randomUUID(), 1, "msg-key", STARTED);
    }
}
