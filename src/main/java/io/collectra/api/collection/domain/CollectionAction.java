package io.collectra.api.collection.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "collection_actions")
public class CollectionAction extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "action_type", nullable = false, length = 80)
    private String actionType;

    @Column(length = 1000)
    private String description;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollectionPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollectionActionStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected CollectionAction() {}

    public CollectionAction(
            UUID tenantId,
            UUID caseId,
            String actionType,
            String description,
            Instant dueAt,
            CollectionPriority priority) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.caseId = Objects.requireNonNull(caseId);
        this.actionType = required(actionType);
        this.description = trim(description);
        this.dueAt = Objects.requireNonNull(dueAt);
        this.priority = priority == null ? CollectionPriority.NORMAL : priority;
        this.status = CollectionActionStatus.PENDING;
    }

    public void complete(Instant at) {
        requirePending();
        status = CollectionActionStatus.COMPLETED;
        completedAt = Objects.requireNonNull(at);
    }

    public void cancel(Instant at) {
        requirePending();
        status = CollectionActionStatus.CANCELLED;
        cancelledAt = Objects.requireNonNull(at);
    }

    public boolean isOverdue(Instant now) {
        return status == CollectionActionStatus.PENDING && dueAt.isBefore(now);
    }

    private void requirePending() {
        if (status != CollectionActionStatus.PENDING) {
            throw new IllegalStateException("Collection action is already terminal");
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCaseId() { return caseId; }
    public String getActionType() { return actionType; }
    public String getDescription() { return description; }
    public Instant getDueAt() { return dueAt; }
    public CollectionPriority getPriority() { return priority; }
    public CollectionActionStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("actionType is required");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
