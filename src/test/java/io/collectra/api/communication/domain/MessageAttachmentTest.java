package io.collectra.api.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.document.domain.OutputFormat;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageAttachmentTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-12T10:00:00Z");
    private static final Instant READY_AT = Instant.parse("2026-09-12T10:05:00Z");

    @Test
    void pendingPdfInitializesExpectedState() {
        MessageAttachment attachment = pending(true);

        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.PENDING);
        assertThat(attachment.getOutputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(attachment.getContentType()).isEqualTo("application/pdf");
        assertThat(attachment.isRequired()).isTrue();
        assertThat(attachment.getGeneratedDocumentId()).isNull();
        assertThat(attachment.getReadyAt()).isNull();
        assertThat(attachment.getFailedAt()).isNull();
    }

    @Test
    void pendingTransitionsToReadyExactlyOnce() {
        MessageAttachment attachment = pending(true);
        UUID documentId = UUID.randomUUID();

        assertThat(attachment.markReady(documentId, READY_AT)).isTrue();
        assertThat(attachment.markReady(documentId, READY_AT.plusSeconds(1))).isFalse();

        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.READY);
        assertThat(attachment.getGeneratedDocumentId()).isEqualTo(documentId);
        assertThat(attachment.getReadyAt()).isEqualTo(READY_AT);
        assertThat(attachment.getFailedAt()).isNull();
    }

    @Test
    void readyCannotBeReboundToDifferentDocument() {
        MessageAttachment attachment = pending(true);
        attachment.markReady(UUID.randomUUID(), READY_AT);

        assertThatThrownBy(() -> attachment.markReady(UUID.randomUUID(), READY_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another generated document");
    }

    @Test
    void pendingTransitionsToFailedAndTruncatesLongMessage() {
        MessageAttachment attachment = pending(true);
        String longMessage = "x".repeat(1_500);

        assertThat(attachment.markFailed("PDF_RENDER_FAILED", longMessage, READY_AT)).isTrue();

        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.FAILED);
        assertThat(attachment.getFailureCode()).isEqualTo("PDF_RENDER_FAILED");
        assertThat(attachment.getFailureMessage()).hasSize(1_000);
        assertThat(attachment.getFailedAt()).isEqualTo(READY_AT);
        assertThat(attachment.getGeneratedDocumentId()).isNull();
    }

    @Test
    void terminalReadyIgnoresFailureCallback() {
        MessageAttachment attachment = pending(true);
        attachment.markReady(UUID.randomUUID(), READY_AT);

        assertThat(attachment.markFailed("FAIL", "late callback", READY_AT.plusSeconds(1))).isFalse();
        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.READY);
    }

    @Test
    void terminalFailedIgnoresSuccessCallback() {
        MessageAttachment attachment = pending(true);
        attachment.markFailed("FAIL", "failed", READY_AT);

        assertThat(attachment.markReady(UUID.randomUUID(), READY_AT.plusSeconds(1))).isFalse();
        assertThat(attachment.getStatus()).isEqualTo(MessageAttachmentStatus.FAILED);
    }

    @Test
    void rejectsBlankFilenameAndOverlongFailureCode() {
        assertThatThrownBy(
                        () ->
                                MessageAttachment.pendingPdf(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        " ",
                                        true,
                                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename");

        MessageAttachment attachment = pending(true);
        assertThatThrownBy(
                        () ->
                                attachment.markFailed(
                                        "X".repeat(81), "failure", READY_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureCode");
    }

    private MessageAttachment pending(boolean required) {
        return MessageAttachment.pendingPdf(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "invoice.pdf",
                required,
                CREATED_AT);
    }
}
