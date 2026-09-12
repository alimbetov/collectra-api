package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.collectra.api.communication.domain.MessageAttachment;
import io.collectra.api.communication.infrastructure.MessageAttachmentRepository;
import io.collectra.api.document.application.DocumentObjectNotFoundException;
import io.collectra.api.document.application.GeneratedOutputService;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class MessageAttachmentContentResolverTest {
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    private final MessageAttachmentRepository attachments = mock(MessageAttachmentRepository.class);
    private final GeneratedDocumentRepository documents = mock(GeneratedDocumentRepository.class);
    private final GeneratedOutputService outputs = mock(GeneratedOutputService.class);
    private final AttachmentProperties properties = new AttachmentProperties();
    private final MessageAttachmentContentResolver resolver =
            new MessageAttachmentContentResolver(attachments, documents, outputs, properties);

    @Test
    void requiredPendingAttachmentFailsBeforeAnyObjectRead() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageAttachment pending = pending(tenantId, messageId, true);
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId))
                .thenReturn(List.of(pending));

        assertResolutionFailure(
                () -> resolver.resolve(tenantId, messageId),
                "ATTACHMENT_NOT_READY",
                DeliveryFailureKind.PERMANENT);

        verify(documents, never()).findByIdAndTenantId(any(), any());
        verify(outputs, never()).read(any());
    }

    @Test
    void optionalPendingAttachmentDoesNotBlockDelivery() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId))
                .thenReturn(List.of(pending(tenantId, messageId, false)));

        assertThat(resolver.resolve(tenantId, messageId)).isEmpty();
        verify(outputs, never()).read(any());
    }

    @Test
    void fileSizeLimitIsCheckedBeforeStorageRead() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MessageAttachment ready = ready(tenantId, messageId, true, documentId);
        GeneratedDocument document = generated(tenantId, ready, documentId, 11L * 1024 * 1024);
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId))
                .thenReturn(List.of(ready));
        when(documents.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));

        assertResolutionFailure(
                () -> resolver.resolve(tenantId, messageId),
                "ATTACHMENT_TOO_LARGE",
                DeliveryFailureKind.PERMANENT);

        verify(outputs, never()).read(any());
    }

    @Test
    void totalSizeLimitIsCheckedBeforeStorageRead() {
        properties.setMaxFileSize(DataSize.ofMegabytes(15));
        properties.setMaxTotalSize(DataSize.ofMegabytes(20));
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageAttachment first = ready(tenantId, messageId, true, UUID.randomUUID());
        MessageAttachment second = ready(tenantId, messageId, true, UUID.randomUUID());
        GeneratedDocument firstDocument =
                generated(tenantId, first, first.getGeneratedDocumentId(), 11L * 1024 * 1024);
        GeneratedDocument secondDocument =
                generated(tenantId, second, second.getGeneratedDocumentId(), 11L * 1024 * 1024);
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId))
                .thenReturn(List.of(first, second));
        when(documents.findByIdAndTenantId(first.getGeneratedDocumentId(), tenantId))
                .thenReturn(Optional.of(firstDocument));
        when(documents.findByIdAndTenantId(second.getGeneratedDocumentId(), tenantId))
                .thenReturn(Optional.of(secondDocument));

        assertResolutionFailure(
                () -> resolver.resolve(tenantId, messageId),
                "ATTACHMENT_TOTAL_SIZE_EXCEEDED",
                DeliveryFailureKind.PERMANENT);

        verify(outputs, never()).read(any());
    }

    @Test
    void missingObjectIsPermanentFailure() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MessageAttachment ready = ready(tenantId, messageId, true, documentId);
        GeneratedDocument document = generated(tenantId, ready, documentId, 3);
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId))
                .thenReturn(List.of(ready));
        when(documents.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(outputs.read(document))
                .thenThrow(
                        new DocumentObjectNotFoundException(
                                "missing.pdf", new RuntimeException("404")));

        assertResolutionFailure(
                () -> resolver.resolve(tenantId, messageId),
                "ATTACHMENT_OBJECT_MISSING",
                DeliveryFailureKind.PERMANENT);
    }

    @Test
    void transientStorageReadFailureIsRetryable() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MessageAttachment ready = ready(tenantId, messageId, true, documentId);
        GeneratedDocument document = generated(tenantId, ready, documentId, 3);
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId))
                .thenReturn(List.of(ready));
        when(documents.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(outputs.read(document)).thenThrow(new RuntimeException("storage unavailable"));

        assertResolutionFailure(
                () -> resolver.resolve(tenantId, messageId),
                "ATTACHMENT_STORAGE_READ_FAILED",
                DeliveryFailureKind.RETRYABLE);
    }

    @Test
    void successfulResolutionReturnsProviderNeutralAttachment() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MessageAttachment ready = ready(tenantId, messageId, true, documentId);
        GeneratedDocument document = generated(tenantId, ready, documentId, 3);
        when(attachments.findAllByTenantIdAndMessageIdOrderByCreatedAtAsc(tenantId, messageId))
                .thenReturn(List.of(ready));
        when(documents.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(outputs.read(document)).thenReturn(new byte[] {1, 2, 3});

        List<DeliveryAttachment> result = resolver.resolve(tenantId, messageId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).filename()).isEqualTo("invoice.pdf");
        assertThat(result.get(0).contentType()).isEqualTo("application/pdf");
        assertThat(result.get(0).content()).containsExactly(1, 2, 3);
    }

    private void assertResolutionFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code,
            DeliveryFailureKind kind) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        AttachmentResolutionException.class,
                        failure -> {
                            assertThat(failure.code()).isEqualTo(code);
                            assertThat(failure.kind()).isEqualTo(kind);
                        });
    }

    private MessageAttachment pending(UUID tenantId, UUID messageId, boolean required) {
        return MessageAttachment.pendingPdf(
                tenantId, messageId, UUID.randomUUID(), "invoice.pdf", required, NOW);
    }

    private MessageAttachment ready(
            UUID tenantId, UUID messageId, boolean required, UUID documentId) {
        MessageAttachment attachment = pending(tenantId, messageId, required);
        attachment.markReady(documentId, NOW.plusSeconds(1));
        return attachment;
    }

    private GeneratedDocument generated(
            UUID tenantId, MessageAttachment relation, UUID documentId, long size) {
        GeneratedDocument document = mock(GeneratedDocument.class);
        when(document.getId()).thenReturn(documentId);
        when(document.getTenantId()).thenReturn(tenantId);
        when(document.getGenerationJobId()).thenReturn(relation.getGenerationJobId());
        when(document.getFormat()).thenReturn(OutputFormat.PDF);
        when(document.getSizeBytes()).thenReturn(size);
        return document;
    }
}
