package io.collectra.api.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageTest {
    private static final Instant STARTED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant RETRY_AT = Instant.parse("2026-09-11T10:05:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-09-11T10:01:00Z");

    @Test
    void createsQueuedEmailSnapshot() {
        Message message = queuedEmail();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getDestination()).isEqualTo("customer@example.com");
        assertThat(message.getResolvedLocale()).isEqualTo("ru-KZ");
        assertThat(message.getSubject()).isEqualTo("Payment reminder");
        assertThat(message.getBody()).isEqualTo("<p>Please pay</p>");
        assertThat(message.getProcessingStartedAt()).isNull();
        assertThat(message.getNextRetryAt()).isNull();
        assertThat(message.getSentAt()).isNull();
    }

    @Test
    void requiresEmailSubjectAndSnapshotContent() {
        assertThatThrownBy(() -> queuedEmail(null, "customer@example.com", "ru-KZ", "body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(() -> queuedEmail("subject", " ", "ru-KZ", "body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destination");
        assertThatThrownBy(() -> queuedEmail("subject", "customer@example.com", " ", "body"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolvedLocale");
        assertThatThrownBy(() -> queuedEmail("subject", "customer@example.com", "ru-KZ", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
    }

    @Test
    void transitionsFromQueuedToProcessingAndSent() {
        Message message = queuedEmail();

        message.beginAttempt(STARTED_AT);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.PROCESSING);
        assertThat(message.getAttemptCount()).isOne();
        assertThat(message.getProcessingStartedAt()).isEqualTo(STARTED_AT);

        message.markSent(null, SENT_AT);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(message.getSentAt()).isEqualTo(SENT_AT);
        assertThat(message.getProviderMessageId()).isNull();
        assertThat(message.getProcessingStartedAt()).isNull();
        assertThat(message.getLastErrorCode()).isNull();
    }

    @Test
    void schedulesRetryAndRequeuesWithoutIncreasingAttemptCount() {
        Message message = queuedEmail();
        message.beginAttempt(STARTED_AT);

        message.scheduleRetry(RETRY_AT, "TEMPORARY", "Provider unavailable");
        assertThat(message.getStatus()).isEqualTo(MessageStatus.RETRY_WAIT);
        assertThat(message.getNextRetryAt()).isEqualTo(RETRY_AT);
        assertThat(message.getProcessingStartedAt()).isNull();
        assertThat(message.getLastErrorCode()).isEqualTo("TEMPORARY");

        message.requeue();
        assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(message.getNextRetryAt()).isNull();
        assertThat(message.getAttemptCount()).isOne();

        message.beginAttempt(RETRY_AT);
        assertThat(message.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void marksTerminalFailureAndLimitsDiagnosticFields() {
        Message message = queuedEmail();
        message.beginAttempt(STARTED_AT);

        message.markFailed("E".repeat(100), "x".repeat(1200));

        assertThat(message.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(message.getLastErrorCode()).hasSize(80);
        assertThat(message.getLastErrorMessage()).hasSize(1000);
        assertThat(message.getProcessingStartedAt()).isNull();
    }

    @Test
    void rejectsInvalidTransitionsAndRetryTime() {
        Message sent = queuedEmail();
        sent.beginAttempt(STARTED_AT);
        sent.markSent("provider-id", SENT_AT);

        assertThatThrownBy(() -> sent.beginAttempt(RETRY_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected QUEUED");

        Message failed = queuedEmail();
        failed.beginAttempt(STARTED_AT);
        failed.markFailed("PERMANENT", "Rejected");
        assertThatThrownBy(failed::requeue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected RETRY_WAIT");

        Message retry = queuedEmail();
        retry.beginAttempt(STARTED_AT);
        assertThatThrownBy(() -> retry.scheduleRetry(STARTED_AT, "TEMPORARY", "Rejected"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after processingStartedAt");
        assertThat(retry.getStatus()).isEqualTo(MessageStatus.PROCESSING);
    }

    @Test
    void doesNotMutateStateWhenTransitionArgumentsAreInvalid() {
        Message queued = queuedEmail();
        assertThatThrownBy(() -> queued.beginAttempt(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("now");
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(queued.getAttemptCount()).isZero();

        Message processing = queuedEmail();
        processing.beginAttempt(STARTED_AT);
        assertThatThrownBy(() -> processing.markFailed(" ", "Rejected"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorCode");
        assertThat(processing.getStatus()).isEqualTo(MessageStatus.PROCESSING);

        assertThatThrownBy(() -> processing.markSent("x".repeat(256), SENT_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerMessageId");
        assertThat(processing.getStatus()).isEqualTo(MessageStatus.PROCESSING);
    }

    private Message queuedEmail() {
        return queuedEmail(
                "Payment reminder", "customer@example.com", "ru-KZ", "<p>Please pay</p>");
    }

    private Message queuedEmail(String subject, String destination, String locale, String body) {
        return Message.queued(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommunicationChannel.EMAIL,
                destination,
                locale,
                subject,
                body);
    }
}
