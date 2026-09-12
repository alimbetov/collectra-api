package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageAttachment;
import io.collectra.api.communication.domain.MessageAttachmentStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageAttachmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    private final MessageRepository messages = mock(MessageRepository.class);
    private final MessageAttachmentRepository attachments = mock(MessageAttachmentRepository.class);
    private final GeneratedDocumentRepository documents = mock(GeneratedDocumentRepository.class);
    private final CampaignRunRepository runs = mock(CampaignRunRepository.class);
    private final MessageDeliveryRequestService deliveryRequests =
            mock(MessageDeliveryRequestService.class);
    private final MessageAttachmentService service =
            new MessageAttachmentService(
                    messages,
                    attachments,
                    documents,
                    runs,
                    deliveryRequests,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsPendingRelationBeforeDeliveryEligibility() {
        Message message = queued();
        GenerationJob job = mock(GenerationJob.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(job.getTenantId()).thenReturn(message.getTenantId());
        when(attachments.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MessageAttachment result =
                service.createPendingGeneratedPdf(message, job, "invoice.pdf", true);

        assertThat(result.getStatus()).isEqualTo(MessageAttachmentStatus.PENDING);
        assertThat(result.getMessageId()).isEqualTo(message.getId());
        assertThat(result.getGenerationJobId()).isEqualTo(job.getId());
        assertThat(result.isRequired()).isTrue();
    }

    @Test
    void rejectsRequiredRelationAfterDeliveryWasRequested() {
        Message message = queued();
        message.markDeliveryRequested(NOW.minusSeconds(1));
        GenerationJob job = mock(GenerationJob.class);
        when(job.getTenantId()).thenReturn(message.getTenantId());

        assertThatThrownBy(
                        () -> service.createPendingGeneratedPdf(message, job, "invoice.pdf", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery became eligible");
    }

    @Test
    void completionBindsExactPdfAndRequestsDeliveryOnce() {
        Fixture fixture = fixture(true);
        GeneratedDocument document = generatedDocument(fixture, 123);
        when(documents.findByTenantIdAndGenerationJobIdAndFormat(
                        fixture.tenantId,
                        fixture.attachment.getGenerationJobId(),
                        OutputFormat.PDF))
                .thenReturn(Optional.of(document));

        assertThat(
                        service.generationCompleted(
                                fixture.tenantId, fixture.attachment.getGenerationJobId()))
                .isTrue();
        assertThat(
                        service.generationCompleted(
                                fixture.tenantId, fixture.attachment.getGenerationJobId()))
                .isFalse();

        assertThat(fixture.attachment.getStatus()).isEqualTo(MessageAttachmentStatus.READY);
        assertThat(fixture.attachment.getGeneratedDocumentId()).isEqualTo(document.getId());
        verify(deliveryRequests, times(1)).requestIfEligible(fixture.message);
    }

    @Test
    void completedJobWithoutExactPdfMetadataFailsClosed() {
        Fixture fixture = fixture(true);
        when(documents.findByTenantIdAndGenerationJobIdAndFormat(
                        fixture.tenantId,
                        fixture.attachment.getGenerationJobId(),
                        OutputFormat.PDF))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.generationCompleted(
                                        fixture.tenantId, fixture.attachment.getGenerationJobId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata is missing");

        assertThat(fixture.attachment.getStatus()).isEqualTo(MessageAttachmentStatus.PENDING);
        verify(deliveryRequests, never()).requestIfEligible(fixture.message);
    }

    @Test
    void requiredTerminalFailureFailsMessageAndCountsCampaignOnce() {
        Fixture fixture = fixture(true);
        CampaignRun run = mock(CampaignRun.class);
        when(runs.findLockedByIdAndTenantId(fixture.message.getCampaignRunId(), fixture.tenantId))
                .thenReturn(Optional.of(run));

        assertThat(
                        service.generationFailed(
                                fixture.tenantId,
                                fixture.attachment.getGenerationJobId(),
                                "PDF_RENDER_FAILED",
                                "cannot render"))
                .isTrue();
        assertThat(
                        service.generationFailed(
                                fixture.tenantId,
                                fixture.attachment.getGenerationJobId(),
                                "PDF_RENDER_FAILED",
                                "duplicate"))
                .isFalse();

        assertThat(fixture.attachment.getStatus()).isEqualTo(MessageAttachmentStatus.FAILED);
        assertThat(fixture.message.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(fixture.message.getLastErrorCode())
                .isEqualTo(MessageAttachmentService.ATTACHMENT_GENERATION_FAILED);
        verify(run, times(1)).messageFailed();
        verify(run, times(1)).completeIfTerminal(NOW);
        verify(deliveryRequests, never()).requestIfEligible(fixture.message);
    }

    @Test
    void optionalTerminalFailureDoesNotFailMessageOrTouchCampaignCounters() {
        Fixture fixture = fixture(false);

        assertThat(
                        service.generationFailed(
                                fixture.tenantId,
                                fixture.attachment.getGenerationJobId(),
                                "PDF_RENDER_FAILED",
                                "optional failed"))
                .isTrue();

        assertThat(fixture.attachment.getStatus()).isEqualTo(MessageAttachmentStatus.FAILED);
        assertThat(fixture.message.getStatus()).isEqualTo(MessageStatus.QUEUED);
        verify(deliveryRequests).requestIfEligible(fixture.message);
        verify(runs, never())
                .findLockedByIdAndTenantId(fixture.message.getCampaignRunId(), fixture.tenantId);
    }

    @Test
    void unknownOrCrossTenantCallbackIsIdempotentNoOp() {
        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(attachments.findByTenantIdAndGenerationJobId(tenantId, jobId))
                .thenReturn(Optional.empty());

        assertThat(service.generationCompleted(tenantId, jobId)).isFalse();
        assertThat(service.generationFailed(tenantId, jobId, "FAIL", "failure")).isFalse();

        verify(messages, never())
                .findLockedByIdAndTenantId(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private Fixture fixture(boolean required) {
        Message message = queued();
        MessageAttachment attachment =
                MessageAttachment.pendingPdf(
                        message.getTenantId(),
                        message.getId(),
                        UUID.randomUUID(),
                        "invoice.pdf",
                        required,
                        NOW.minusSeconds(10));
        when(attachments.findByTenantIdAndGenerationJobId(
                        message.getTenantId(), attachment.getGenerationJobId()))
                .thenReturn(Optional.of(attachment));
        when(messages.findLockedByIdAndTenantId(message.getTenantId(), message.getId()))
                .thenReturn(Optional.of(message));
        when(attachments.findLockedByTenantIdAndGenerationJobId(
                        message.getTenantId(), attachment.getGenerationJobId()))
                .thenReturn(Optional.of(attachment));
        return new Fixture(message.getTenantId(), message, attachment);
    }

    private GeneratedDocument generatedDocument(Fixture fixture, long size) {
        GeneratedDocument document = mock(GeneratedDocument.class);
        UUID documentId = UUID.randomUUID();
        when(document.getId()).thenReturn(documentId);
        when(document.getTenantId()).thenReturn(fixture.tenantId);
        when(document.getGenerationJobId()).thenReturn(fixture.attachment.getGenerationJobId());
        when(document.getFormat()).thenReturn(OutputFormat.PDF);
        when(document.getSizeBytes()).thenReturn(size);
        return document;
    }

    private Message queued() {
        return Message.queued(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommunicationChannel.EMAIL,
                "client@example.com",
                "ru",
                "Subject",
                "<p>Body</p>");
    }

    private record Fixture(UUID tenantId, Message message, MessageAttachment attachment) {}
}
