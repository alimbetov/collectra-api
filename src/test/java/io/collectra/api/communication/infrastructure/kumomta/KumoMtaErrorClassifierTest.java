package io.collectra.api.communication.infrastructure.kumomta;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.communication.application.DeliveryFailureKind;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class KumoMtaErrorClassifierTest {
    private final KumoMtaErrorClassifier classifier = new KumoMtaErrorClassifier();

    @Test
    void timeoutRateLimitAndServerErrorsAreRetryable() {
        assertThat(
                        classifier
                                .classify(
                                        new ResourceAccessException(
                                                "timeout", new SocketTimeoutException()))
                                .kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
        assertThat(classifier.classifyStatus(HttpStatus.REQUEST_TIMEOUT).kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
        assertThat(classifier.classifyStatus(HttpStatus.TOO_MANY_REQUESTS).kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
        assertThat(classifier.classifyStatus(HttpStatus.SERVICE_UNAVAILABLE).kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
    }

    @Test
    void authAndInvalidContentArePermanent() {
        assertThat(classifier.classifyStatus(HttpStatus.UNAUTHORIZED).kind())
                .isEqualTo(DeliveryFailureKind.PERMANENT);
        assertThat(classifier.classifyStatus(HttpStatus.FORBIDDEN).kind())
                .isEqualTo(DeliveryFailureKind.PERMANENT);
        assertThat(classifier.classifyStatus(HttpStatus.BAD_REQUEST).kind())
                .isEqualTo(DeliveryFailureKind.PERMANENT);
        assertThat(classifier.classifyStatus(HttpStatus.UNPROCESSABLE_ENTITY).kind())
                .isEqualTo(DeliveryFailureKind.PERMANENT);
    }

    @Test
    void onlyExplicitlyTransientRecipientErrorIsRetryable() {
        var temporary =
                new KumoMtaInjectResponse(
                        0, 1, List.of("a@b.kz"), List.of("temporary overload"));
        var rejected =
                new KumoMtaInjectResponse(
                        0, 1, List.of("a@b.kz"), List.of("mailbox rejected"));

        assertThat(classifier.classifyResponse(temporary).kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
        assertThat(classifier.classifyResponse(rejected).kind())
                .isEqualTo(DeliveryFailureKind.PERMANENT);
    }

    @Test
    void inconsistentTwoHundredResponseIsRetryableProviderFailure() {
        var empty = new KumoMtaInjectResponse(0, 0, List.of(), List.of());
        var impossible = new KumoMtaInjectResponse(2, 0, List.of(), List.of());

        assertThat(classifier.classifyResponse(empty).code()).isEqualTo("KUMO_INVALID_RESPONSE");
        assertThat(classifier.classifyResponse(empty).kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
        assertThat(classifier.classifyResponse(impossible).kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
    }

    @Test
    void providerErrorTextIsNotPersistedInApplicationMessage() {
        var rejected =
                new KumoMtaInjectResponse(
                        0,
                        1,
                        List.of("client@example.com"),
                        List.of("mailbox client@example.com rejected for private content"));

        var result = classifier.classifyResponse(rejected);

        assertThat(result.message())
                .isEqualTo("KumoMTA rejected recipient")
                .doesNotContain("client@example.com", "private content");
    }
}
