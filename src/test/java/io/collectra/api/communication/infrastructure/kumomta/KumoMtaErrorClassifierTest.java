package io.collectra.api.communication.infrastructure.kumomta;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.communication.application.DeliveryFailureKind;
import java.net.SocketTimeoutException;
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
                        0, 1, java.util.List.of("a@b.kz"), java.util.List.of("temporary overload"));
        var rejected =
                new KumoMtaInjectResponse(
                        0, 1, java.util.List.of("a@b.kz"), java.util.List.of("mailbox rejected"));

        assertThat(classifier.classifyResponse(temporary).kind())
                .isEqualTo(DeliveryFailureKind.RETRYABLE);
        assertThat(classifier.classifyResponse(rejected).kind())
                .isEqualTo(DeliveryFailureKind.PERMANENT);
    }
}
