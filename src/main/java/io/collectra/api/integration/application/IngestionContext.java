package io.collectra.api.integration.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record IngestionContext(
        UUID tenantId,
        UUID integrationSourceId,
        UUID serviceClientId,
        UUID ingestionId,
        String idempotencyKey,
        String correlationId,
        Instant receivedAt,
        String contentType,
        String sourceEventId,
        LocalDate businessDate,
        String documentTypeHint,
        Map<String, String> attributes) {
    public IngestionContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
