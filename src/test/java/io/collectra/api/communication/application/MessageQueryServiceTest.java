package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachment;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class MessageQueryServiceTest {
    private static final UUID TENANT = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CAMPAIGN = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID RUN = UUID.fromString("20000000-0000-0000-0000-000000000003");

    @Mock MessageRepository messages;
    @Mock MessageAttachmentRepository attachments;
    @Mock CampaignRunRepository runs;

    private MessageQueryService service;

    @BeforeEach
    void setUp() {
        service =
                new MessageQueryService(
                        messages,
                        attachments,
                        runs,
                        new DestinationMasker(),
                        new DeliveryErrorSummary());
    }

    @ParameterizedTest(name = "page={0}, size={1}")
    @MethodSource("invalidPages")
    void invalidPagingIsRejectedBeforeDatabaseAccess(int page, int size) {
        assertThatThrownBy(
                        () ->
                                service.list(
                                        TENANT,
                                        CAMPAIGN,
                                        RUN,
                                        null,
                                        null,
                                        null,
                                        page,
                                        size))
                .isInstanceOf(IllegalArgumentException.class);

        verify(runs, never()).findByIdAndTenantId(any(), any());
        verify(messages, never())
                .findDeliveryMessages(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void foreignOrMismatchedRunIsNotDisclosed() {
        when(runs.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.list(TENANT, CAMPAIGN, RUN, null, null, null, 0, 50))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Campaign run not found");
        verify(messages, never())
                .findDeliveryMessages(any(), any(), any(), any(), any(), any(), any());

        CampaignRun anotherCampaign = run(UUID.randomUUID());
        when(runs.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.of(anotherCampaign));
        assertThatThrownBy(
                        () -> service.list(TENANT, CAMPAIGN, RUN, null, null, null, 0, 50))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Campaign run not found");
    }

    @Test
    void listForwardsAllTenantScopedFiltersAndUsesStableDescendingSort() {
        CampaignRun run = run(CAMPAIGN);
        UUID customerId = UUID.randomUUID();
        Message message = message(CommunicationChannel.EMAIL, "person@example.test", customerId);
        when(runs.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.of(run));
        when(messages.findDeliveryMessages(
                        any(), any(), any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(message)));
        ArgumentCaptor<PageRequest> pageable = ArgumentCaptor.forClass(PageRequest.class);

        MessageQueryService.MessageSlice result =
                service.list(
                        TENANT,
                        CAMPAIGN,
                        RUN,
                        MessageStatus.QUEUED,
                        CommunicationChannel.EMAIL,
                        customerId,
                        2,
                        25);

        verify(messages)
                .findDeliveryMessages(
                        TENANT,
                        CAMPAIGN,
                        RUN,
                        MessageStatus.QUEUED,
                        CommunicationChannel.EMAIL,
                        customerId,
                        pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id")).isNotNull();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(25);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).maskedDestination()).isEqualTo("p***@example.test");
    }

    @ParameterizedTest(name = "{0} is opaque in list output")
    @MethodSource("nonEmailDestinations")
    void nonEmailDestinationsAreAlwaysOpaque(
            CommunicationChannel channel, String rawDestination) {
        CampaignRun run = run(CAMPAIGN);
        Message message = message(channel, rawDestination, UUID.randomUUID());
        when(runs.findByIdAndTenantId(RUN, TENANT)).thenReturn(Optional.of(run));
        when(messages.findDeliveryMessages(
                        any(), any(), any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(message)));

        MessageQueryService.MessageSlice result =
                service.list(TENANT, CAMPAIGN, RUN, null, channel, null, 0, 50);

        assertThat(result.content().get(0).maskedDestination()).isEqualTo("***");
        assertThat(result.content().get(0).maskedDestination()).doesNotContain(rawDestination);
    }

    @Test
    void detailLookupIsScopedByTenantCampaignAndRunBeforeAttachmentsAreLoaded() {
        UUID messageId = UUID.randomUUID();
        when(messages.findByIdAndTenantIdAndCampaignIdAndCampaignRunId(
                        messageId, TENANT, CAMPAIGN, RUN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(TENANT, CAMPAIGN, RUN, messageId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Message not found");

        verify(attachments, never()).findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void detailMasksDestinationNormalizesErrorAndReturnsAttachmentMetadataOnly() {
        UUID customerId = UUID.randomUUID();
        Message message = message(CommunicationChannel.EMAIL, "alice@example.test", customerId);
        message.beginAttempt(Instant.parse("2026-09-12T10:00:00Z"));
        message.markFailed("  provider_503  ", "sensitive raw provider message");
        MessageAttachment attachment =
                MessageAttachment.pendingPdf(
                        TENANT,
                        message.getId(),
                        UUID.randomUUID(),
                        "invoice.pdf",
                        true,
                        Instant.parse("2026-09-12T09:00:00Z"));
        when(messages.findByIdAndTenantIdAndCampaignIdAndCampaignRunId(
                        message.getId(), TENANT, CAMPAIGN, RUN))
                .thenReturn(Optional.of(message));
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(
                        TENANT, message.getId()))
                .thenReturn(List.of(attachment));

        MessageQueryService.MessageDetail detail =
                service.detail(TENANT, CAMPAIGN, RUN, message.getId());

        assertThat(detail.maskedDestination()).isEqualTo("a***@example.test");
        assertThat(detail.lastErrorCode()).isEqualTo("UNKNOWN");
        assertThat(detail.lastErrorSummary()).isEqualTo("Delivery failed");
        assertThat(detail.lastErrorSummary()).doesNotContain("sensitive raw provider message");
        assertThat(detail.attachments()).hasSize(1);
        assertThat(detail.attachments().get(0).filename()).isEqualTo("invoice.pdf");
        assertThat(detail.attachments().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(detail.attachments().get(0).size()).isNull();
        assertThat(detail.attachments().get(0).required()).isTrue();
    }

    private static Stream<Arguments> invalidPages() {
        return Stream.of(
                Arguments.of(-1, 50),
                Arguments.of(-100, 50),
                Arguments.of(0, 0),
                Arguments.of(0, -1),
                Arguments.of(0, MessageQueryService.MAX_SIZE + 1),
                Arguments.of(1, Integer.MAX_VALUE));
    }

    private static Stream<Arguments> nonEmailDestinations() {
        return Stream.of(
                Arguments.of(CommunicationChannel.SMS, "+77010000000"),
                Arguments.of(CommunicationChannel.WHATSAPP, "+77010000001"),
                Arguments.of(CommunicationChannel.TELEGRAM, "telegram-user-42"),
                Arguments.of(CommunicationChannel.IN_APP, "device-token-secret"));
    }

    private CampaignRun run(UUID campaignId) {
        return new CampaignRun(TENANT, campaignId);
    }

    private Message message(
            CommunicationChannel channel, String destination, UUID customerId) {
        return Message.queued(
                TENANT,
                CAMPAIGN,
                RUN,
                UUID.randomUUID(),
                customerId,
                null,
                UUID.randomUUID(),
                channel,
                destination,
                "ru-KZ",
                channel == CommunicationChannel.EMAIL ? "Reminder" : null,
                "body");
    }
}
