package io.collectra.api.communication.infrastructure.kumomta;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

record KumoMtaInjectRequest(
        @JsonProperty("envelope_sender") String envelopeSender,
        Content content,
        List<KumoMtaRecipient> recipients,
        @JsonProperty("template_dialect") String templateDialect,
        @JsonProperty("deferred_generation") boolean deferredGeneration,
        @JsonProperty("deferred_spool") boolean deferredSpool) {

    record Content(
            Map<String, String> headers,
            @JsonProperty("html_body") String htmlBody,
            List<Attachment> attachments) {}

    record Attachment(
            @JsonProperty("file_name") String fileName,
            @JsonProperty("content_type") String contentType,
            String data,
            boolean base64,
            @JsonProperty("content_id") String contentId) {}
}
