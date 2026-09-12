package io.collectra.api.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class MessageScenarioMatrixTest {
    private static final Instant T0 = Instant.parse("2026-09-12T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @ParameterizedTest
    @EnumSource(CommunicationChannel.class)
    void allChannelsPreserveProviderNeutralSnapshot(CommunicationChannel channel) {
        String subject = channel == CommunicationChannel.EMAIL ? "  Reminder  " : null;
        Message message = queued(channel, destination(channel), "  ru-KZ  ", subject, "  body  ");

        assertThat(message.getChannel()).isEqualTo(channel);
        assertThat(message.getDestination()).isEqualTo(destination(channel));
        assertThat(message.getResolvedLocale()).isEqualTo("ru-KZ");
        assertThat(message.getSubject())
                .isEqualTo(channel == CommunicationChannel.EMAIL ? "Reminder" : null);
        assertThat(message.getBody()).isEqualTo("body");
        assertThat(message.getStatus()).isEqualTo(MessageStatus.QUEUED);
    }

    @ParameterizedTest(name = "required field {0}")
    @MethodSource("invalidRequiredFields")
    void requiredCreationFieldsFailClosed(String field, ThrowingFactory factory) {
        assertThatThrownBy(factory::create)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(field);
    }

    @ParameterizedTest(name = "length boundary {0}")
    @MethodSource("lengthBoundaries")
    void snapshotLengthBoundariesAreEnforced(
            String field, int allowedLength, int rejectedLength, SnapshotField snapshotField) {
        String allowed = "x".repeat(allowedLength);
        String rejected = "x".repeat(rejectedLength);

        Message valid = snapshotField.create(allowed);
        assertThat(snapshotField.read(valid)).hasSize(allowedLength);

        assertThatThrownBy(() -> snapshotField.create(rejected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(field);
    }

    @Test
    void deliveryRequestedIsIdempotentAndAllowedOnlyWhileQueued() {
        Message message = queuedEmail();
        assertThat(message.markDeliveryRequested(T0)).isTrue();
        assertThat(message.getDeliveryRequestedAt()).isEqualTo(T0);
        assertThat(message.markDeliveryRequested(T1)).isFalse();
        assertThat(message.getDeliveryRequestedAt()).isEqualTo(T0);

        Message processing = queuedEmail();
        processing.beginAttempt(T0);
        assertThatThrownBy(() -> processing.markDeliveryRequested(T1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected QUEUED");
    }

    @ParameterizedTest(name = "attempt {0}")
    @MethodSource("attemptCounts")
    void attemptCountMeansActualProcessingClaims(int attempts) {
        Message message = queuedEmail();
        Instant cursor = T0;
        for (int i = 1; i <= attempts; i++) {
            message.beginAttempt(cursor);
            assertThat(message.getAttemptCount()).isEqualTo(i);
            if (i < attempts) {
                message.scheduleRetry(cursor.plusSeconds(1), "TEMP", "retry");
                message.requeue();
                cursor = cursor.plusSeconds(10);
            }
        }
        assertThat(message.getStatus()).isEqualTo(MessageStatus.PROCESSING);
    }

    @ParameterizedTest(name = "illegal terminal transition from {0}")
    @MethodSource("illegalTerminalStates")
    void terminalStatesNeverMoveBackToProcessing(String name, Message message) {
        assertThatThrownBy(() -> message.beginAttempt(T1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected QUEUED");
    }

    @Test
    void successfulSendClearsRetryAndDiagnosticState() {
        Message message = queuedEmail();
        message.beginAttempt(T0);
        message.scheduleRetry(T1, "TEMP", "retry");
        message.requeue();
        message.beginAttempt(T1.plusSeconds(1));
        message.markSent(" provider-42 ", T1.plusSeconds(2));

        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(message.getProviderMessageId()).isEqualTo("provider-42");
        assertThat(message.getProcessingStartedAt()).isNull();
        assertThat(message.getNextRetryAt()).isNull();
        assertThat(message.getLastErrorCode()).isNull();
        assertThat(message.getLastErrorMessage()).isNull();
    }

    @ParameterizedTest(name = "error normalization {0}")
    @MethodSource("errorNormalizationCases")
    void diagnosticFieldsAreNormalized(
            String code, String messageText, int codeLength, Integer messageLength) {
        Message message = queuedEmail();
        message.beginAttempt(T0);
        message.markFailed(code, messageText);

        assertThat(message.getLastErrorCode()).hasSize(codeLength);
        if (messageLength == null) {
            assertThat(message.getLastErrorMessage()).isNull();
        } else {
            assertThat(message.getLastErrorMessage()).hasSize(messageLength);
        }
    }

    @ParameterizedTest
    @MethodSource("badRetryTimes")
    void retryMustBeStrictlyAfterProcessingStart(Instant retryAt) {
        Message message = queuedEmail();
        message.beginAttempt(T0);

        assertThatThrownBy(() -> message.scheduleRetry(retryAt, "TEMP", "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after processingStartedAt");
        assertThat(message.getStatus()).isEqualTo(MessageStatus.PROCESSING);
    }

    @Test
    void blankOptionalProviderIdAndErrorMessageNormalizeToNull() {
        Message sent = queuedEmail();
        sent.beginAttempt(T0);
        sent.markSent("   ", T1);
        assertThat(sent.getProviderMessageId()).isNull();

        Message failed = queuedEmail();
        failed.beginAttempt(T0);
        failed.markFailed(" PERMANENT ", "   ");
        assertThat(failed.getLastErrorCode()).isEqualTo("PERMANENT");
        assertThat(failed.getLastErrorMessage()).isNull();
    }

    private static Stream<Arguments> invalidRequiredFields() {
        return Stream.of(
                Arguments.of(
                        "tenantId",
                        (ThrowingFactory)
                                () ->
                                        queuedWithIds(
                                                null,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID())),
                Arguments.of(
                        "campaignId",
                        (ThrowingFactory)
                                () ->
                                        queuedWithIds(
                                                UUID.randomUUID(),
                                                null,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID())),
                Arguments.of(
                        "campaignRunId",
                        (ThrowingFactory)
                                () ->
                                        queuedWithIds(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                null,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID())),
                Arguments.of(
                        "campaignRecipientId",
                        (ThrowingFactory)
                                () ->
                                        queuedWithIds(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                null,
                                                UUID.randomUUID(),
                                                UUID.randomUUID())),
                Arguments.of(
                        "customerId",
                        (ThrowingFactory)
                                () ->
                                        queuedWithIds(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                null,
                                                UUID.randomUUID())),
                Arguments.of(
                        "templateVersionId",
                        (ThrowingFactory)
                                () ->
                                        queuedWithIds(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                null)),
                Arguments.of(
                        "channel",
                        (ThrowingFactory)
                                () ->
                                        Message.queued(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                null,
                                                UUID.randomUUID(),
                                                null,
                                                "dest",
                                                "ru-KZ",
                                                null,
                                                "body")),
                Arguments.of(
                        "destination",
                        (ThrowingFactory)
                                () -> queued(CommunicationChannel.SMS, " ", "ru-KZ", null, "body")),
                Arguments.of(
                        "resolvedLocale",
                        (ThrowingFactory)
                                () ->
                                        queued(
                                                CommunicationChannel.SMS,
                                                "+77010000000",
                                                " ",
                                                null,
                                                "body")),
                Arguments.of(
                        "body",
                        (ThrowingFactory)
                                () ->
                                        queued(
                                                CommunicationChannel.SMS,
                                                "+77010000000",
                                                "ru-KZ",
                                                null,
                                                " ")),
                Arguments.of(
                        "subject",
                        (ThrowingFactory)
                                () ->
                                        queued(
                                                CommunicationChannel.EMAIL,
                                                "a@example.test",
                                                "ru-KZ",
                                                " ",
                                                "body")));
    }

    private static Stream<Arguments> lengthBoundaries() {
        return Stream.of(
                Arguments.of("destination", 500, 501, SnapshotField.DESTINATION),
                Arguments.of("resolvedLocale", 35, 36, SnapshotField.LOCALE),
                Arguments.of("subject", 500, 501, SnapshotField.SUBJECT));
    }

    private static Stream<Arguments> attemptCounts() {
        return Stream.of(
                Arguments.of(1),
                Arguments.of(2),
                Arguments.of(3),
                Arguments.of(4),
                Arguments.of(5));
    }

    private static Stream<Arguments> illegalTerminalStates() {
        Message sent = queuedEmail();
        sent.beginAttempt(T0);
        sent.markSent("id", T1);
        Message failed = queuedEmail();
        failed.beginAttempt(T0);
        failed.markFailed("PERMANENT", "failed");
        return Stream.of(Arguments.of("SENT", sent), Arguments.of("FAILED", failed));
    }

    private static Stream<Arguments> errorNormalizationCases() {
        return Stream.of(
                Arguments.of("E", "m", 1, 1),
                Arguments.of(" E ", " message ", 1, 7),
                Arguments.of("E".repeat(80), "m".repeat(1000), 80, 1000),
                Arguments.of("E".repeat(81), "m".repeat(1001), 80, 1000),
                Arguments.of("E".repeat(200), null, 80, null));
    }

    private static Stream<Arguments> badRetryTimes() {
        return Stream.of(
                Arguments.of(T0),
                Arguments.of(T0.minusNanos(1)),
                Arguments.of(T0.minusSeconds(60)));
    }

    private static String destination(CommunicationChannel channel) {
        return switch (channel) {
            case EMAIL -> "customer@example.test";
            case SMS, WHATSAPP -> "+77010000000";
            case TELEGRAM -> "telegram-user-42";
            case IN_APP -> "device-token-42";
        };
    }

    private static Message queuedEmail() {
        return queued(
                CommunicationChannel.EMAIL, "customer@example.test", "ru-KZ", "Reminder", "body");
    }

    private static Message queued(
            CommunicationChannel channel,
            String destination,
            String locale,
            String subject,
            String body) {
        return Message.queued(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                channel,
                destination,
                locale,
                subject,
                body);
    }

    private static Message queuedWithIds(
            UUID tenantId,
            UUID campaignId,
            UUID campaignRunId,
            UUID campaignRecipientId,
            UUID customerId,
            UUID templateVersionId) {
        return Message.queued(
                tenantId,
                campaignId,
                campaignRunId,
                campaignRecipientId,
                customerId,
                null,
                templateVersionId,
                CommunicationChannel.EMAIL,
                "customer@example.test",
                "ru-KZ",
                "Reminder",
                "body");
    }

    @FunctionalInterface
    private interface ThrowingFactory {
        Message create();
    }

    private enum SnapshotField {
        DESTINATION {
            @Override
            Message create(String value) {
                return queued(CommunicationChannel.SMS, value, "ru-KZ", null, "body");
            }

            @Override
            String read(Message message) {
                return message.getDestination();
            }
        },
        LOCALE {
            @Override
            Message create(String value) {
                return queued(CommunicationChannel.SMS, "+77010000000", value, null, "body");
            }

            @Override
            String read(Message message) {
                return message.getResolvedLocale();
            }
        },
        SUBJECT {
            @Override
            Message create(String value) {
                return queued(
                        CommunicationChannel.EMAIL,
                        "customer@example.test",
                        "ru-KZ",
                        value,
                        "body");
            }

            @Override
            String read(Message message) {
                return message.getSubject();
            }
        };

        abstract Message create(String value);

        abstract String read(Message message);
    }
}
