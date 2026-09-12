package io.collectra.api.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "collection_events")
public class CollectionEvent {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "actor", length = 200)
    private String actor;

    @Column(name = "summary", length = 1000)
    private String summary;

    protected CollectionEvent() {}

    public CollectionEvent(
            UUID tenantId,
            UUID caseId,
            String eventType,
            String entityType,
            UUID entityId,
            Instant eventAt,
            String actor,
            String summary) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.caseId = Objects.requireNonNull(caseId);
        this.eventType = required(eventType);
        this.entityType = required(entityType);
        this.entityId = Objects.requireNonNull(entityId);
        this.eventAt = Objects.requireNonNull(eventAt);
        this.actor = trim(actor);
        this.summary = trim(summary);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCaseId() { return caseId; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Instant getEventAt() { return eventAt; }
    public String getActor() { return actor; }
    public String getSummary() { return summary; }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
