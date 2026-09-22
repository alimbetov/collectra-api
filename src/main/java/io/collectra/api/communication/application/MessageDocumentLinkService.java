package io.collectra.api.communication.application;

import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.infrastructure.CampaignRunRepository;
import io.collectra.api.communication.domain.Message;
import io.collectra.api.communication.domain.MessageDocumentLink;
import io.collectra.api.communication.domain.MessageDocumentLinkStatus;
import io.collectra.api.communication.domain.MessageStatus;
import io.collectra.api.communication.infrastructure.MessageDocumentLinkRepository;
import io.collectra.api.communication.infrastructure.MessageRepository;
import io.collectra.api.document.application.GeneratedOutputService;
import io.collectra.api.document.domain.GeneratedDocument;
import io.collectra.api.document.domain.GenerationJob;
import io.collectra.api.document.domain.OutputFormat;
import io.collectra.api.document.infrastructure.GeneratedDocumentRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageDocumentLinkService {
    public static final String DOCUMENT_LINK_GENERATION_FAILED = "DOCUMENT_LINK_GENERATION_FAILED";

    private final MessageDocumentLinkRepository links;
    private final MessageRepository messages;
    private final GeneratedDocumentRepository documents;
    private final GeneratedOutputService outputs;
    private final CampaignRunRepository runs;
    private final MessageDeliveryRequestService deliveryRequests;
    private final DocumentLinkProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MessageDocumentLinkService(
            MessageDocumentLinkRepository links,
            MessageRepository messages,
            GeneratedDocumentRepository documents,
            GeneratedOutputService outputs,
            CampaignRunRepository runs,
            MessageDeliveryRequestService deliveryRequests,
            DocumentLinkProperties properties,
            Clock clock) {
        this.links = links;
        this.messages = messages;
        this.documents = documents;
        this.outputs = outputs;
        this.runs = runs;
        this.deliveryRequests = deliveryRequests;
        this.properties = properties;
        this.clock = clock;
    }

    public PreparedLink prepare() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.getTtl());
        String base = properties.getPublicBaseUrl().toString().replaceAll("/+$", "");
        URI url = URI.create(base + "/api/v1/public/documents/" + token);
        return new PreparedLink(token, sha256(token), url, expiresAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MessageDocumentLink createPending(
            Message message, GenerationJob job, PreparedLink preparedLink) {
        if (!message.getTenantId().equals(job.getTenantId())) {
            throw new IllegalArgumentException("Message and generation job tenants differ");
        }
        return links.save(
                new MessageDocumentLink(
                        message.getTenantId(),
                        message.getId(),
                        job.getId(),
                        preparedLink.tokenHash(),
                        true,
                        preparedLink.expiresAt(),
                        clock.instant()));
    }

    @Transactional
    public boolean generationCompleted(UUID tenantId, UUID generationJobId) {
        MessageDocumentLink correlation =
                links.findByTenantIdAndGenerationJobId(tenantId, generationJobId).orElse(null);
        if (correlation == null) {
            return false;
        }

        Message message = lockedMessage(tenantId, correlation.getMessageId());
        MessageDocumentLink link =
                links.findLockedByTenantIdAndGenerationJobId(tenantId, generationJobId)
                        .orElseThrow(() -> new NoSuchElementException("Document link not found"));
        if (link.getStatus() != MessageDocumentLinkStatus.PENDING) {
            return false;
        }

        GeneratedDocument document =
                documents.findByTenantIdAndGenerationJobIdAndFormat(
                                tenantId, generationJobId, OutputFormat.PDF)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Generated PDF metadata is missing for completed job"));
        link.markReady(document.getId(), clock.instant());
        deliveryRequests.requestIfEligible(message);
        return true;
    }

    @Transactional
    public boolean generationFailed(
            UUID tenantId, UUID generationJobId, String errorCode, String errorMessage) {
        MessageDocumentLink correlation =
                links.findByTenantIdAndGenerationJobId(tenantId, generationJobId).orElse(null);
        if (correlation == null) {
            return false;
        }

        Message message = lockedMessage(tenantId, correlation.getMessageId());
        MessageDocumentLink link =
                links.findLockedByTenantIdAndGenerationJobId(tenantId, generationJobId)
                        .orElseThrow(() -> new NoSuchElementException("Document link not found"));
        if (!link.markFailed(
                errorCode == null || errorCode.isBlank() ? "GENERATION_FAILED" : errorCode,
                errorMessage,
                clock.instant())) {
            return false;
        }

        if (message.getStatus() != MessageStatus.QUEUED) {
            return true;
        }
        CampaignRun run =
                runs.findLockedByIdAndTenantId(message.getCampaignRunId(), tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Campaign run not found"));
        message.markFailedBeforeDelivery(
                DOCUMENT_LINK_GENERATION_FAILED,
                errorMessage == null || errorMessage.isBlank()
                        ? "Required linked PDF generation failed"
                        : errorMessage);
        run.messageFailed();
        run.completeIfTerminal(clock.instant());
        return true;
    }

    @Transactional(readOnly = true)
    public PublicDocument open(String token) {
        MessageDocumentLink link =
                links.findByTokenHash(sha256(token))
                        .filter(value -> value.getStatus() == MessageDocumentLinkStatus.READY)
                        .filter(value -> value.getExpiresAt().isAfter(clock.instant()))
                        .orElseThrow(() -> new NoSuchElementException("Document link not found"));

        GeneratedDocument document =
                documents.findByIdAndTenantId(link.getGeneratedDocumentId(), link.getTenantId())
                        .orElseThrow(() -> new NoSuchElementException("Document link not found"));
        return new PublicDocument(document.getMediaType(), outputs.read(document));
    }

    private Message lockedMessage(UUID tenantId, UUID messageId) {
        return messages.findLockedByIdAndTenantId(tenantId, messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));
    }

    private String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record PreparedLink(String token, String tokenHash, URI url, Instant expiresAt) {}

    public record PublicDocument(String mediaType, byte[] content) {}
}
