package io.collectra.api.communication.infrastructure.kumomta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.communication.application.DeliveryCommand;
import io.collectra.api.communication.application.DeliveryFailureKind;
import io.collectra.api.communication.application.DeliveryResult;
import io.collectra.api.communication.domain.CommunicationChannel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KumoMtaEmailDeliveryGatewayTest {
    private final KumoMtaClient client = mock(KumoMtaClient.class);
    private final KumoMtaProperties properties = properties();
    private final KumoMtaEmailDeliveryGateway gateway =
            new KumoMtaEmailDeliveryGateway(client, properties, new KumoMtaErrorClassifier());

    @Test
    void mapsRenderedEmailToStaticSingleRecipientRequest() {
        DeliveryCommand command = email("Subject {{already-rendered}}", "<p>Body {{fixed}}</p>");
        when(client.inject(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new KumoMtaInjectResponse(1, 0, List.of(), List.of()));
        ArgumentCaptor<KumoMtaInjectRequest> request =
                ArgumentCaptor.forClass(KumoMtaInjectRequest.class);

        DeliveryResult result = gateway.deliver(command);

        verify(client).inject(request.capture());
        assertThat(result).isEqualTo(new DeliveryResult.Accepted(null));
        assertThat(request.getValue().envelopeSender()).isEqualTo("bounce@collectra.kz");
        assertThat(request.getValue().content().headers())
                .containsEntry("From", "Collectra <noreply@collectra.kz>")
                .containsEntry("Subject", "Subject {{already-rendered}}")
                .doesNotContainKey("To");
        assertThat(request.getValue().content().htmlBody()).isEqualTo("<p>Body {{fixed}}</p>");
        assertThat(request.getValue().recipients()).hasSize(1);
        assertThat(request.getValue().recipients().get(0).email()).isEqualTo(command.destination());
        assertThat(request.getValue().recipients().get(0).metadata())
                .containsEntry("collectra_message_id", command.messageId().toString())
                .containsEntry("collectra_tenant_id", command.tenantId().toString());
        assertThat(request.getValue().templateDialect()).isEqualTo("Static");
        assertThat(request.getValue().deferredGeneration()).isFalse();
        assertThat(request.getValue().deferredSpool()).isFalse();
    }

    @Test
    void twoHundredWithFailedRecipientIsPermanentRejection() {
        when(client.inject(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        new KumoMtaInjectResponse(
                                0, 1, List.of("client@example.com"), List.of("mailbox rejected")));

        DeliveryResult.Rejected result = (DeliveryResult.Rejected) gateway.deliver(email("S", "B"));

        assertThat(result.kind()).isEqualTo(DeliveryFailureKind.PERMANENT);
        assertThat(result.code()).isEqualTo("KUMO_RECIPIENT_REJECTED");
    }

    @Test
    void doesNotSendUnsupportedChannelOrInvalidEmail() {
        DeliveryCommand sms =
                new DeliveryCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        CommunicationChannel.SMS,
                        "+77010000000",
                        null,
                        "body");

        assertThat(((DeliveryResult.Rejected) gateway.deliver(sms)).code())
                .isEqualTo("UNSUPPORTED_CHANNEL");
        assertThat(((DeliveryResult.Rejected) gateway.deliver(email("S", "B", "invalid"))).code())
                .isEqualTo("INVALID_DESTINATION");
    }

    private DeliveryCommand email(String subject, String body) {
        return email(subject, body, "client@example.com");
    }

    private DeliveryCommand email(String subject, String body, String destination) {
        return new DeliveryCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommunicationChannel.EMAIL,
                destination,
                subject,
                body);
    }

    private KumoMtaProperties properties() {
        KumoMtaProperties result = new KumoMtaProperties();
        result.setBaseUrl("http://127.0.0.1:8000");
        result.setEnvelopeSender("bounce@collectra.kz");
        result.setFromHeader("Collectra <noreply@collectra.kz>");
        return result;
    }
}
