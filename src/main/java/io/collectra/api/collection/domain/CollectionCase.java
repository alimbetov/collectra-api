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
@Table(name = "collection_cases")
public class CollectionCase extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CollectionCaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollectionPriority priority;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 30)
    private CollectionCloseReason closeReason;

    protected CollectionCase() {}

    public CollectionCase(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            CollectionPriority priority,
            UUID assignedTo,
            Instant openedAt) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = Objects.requireNonNull(customerId);
        this.invoiceId = Objects.requireNonNull(invoiceId);
        this.status = CollectionCaseStatus.OPEN;
        this.priority = priority == null ? CollectionPriority.NORMAL : priority;
        this.assignedTo = assignedTo;
        this.openedAt = Objects.requireNonNull(openedAt);
    }

    public void update(CollectionPriority priority, UUID assignedTo) {
        if (status == CollectionCaseStatus.CLOSED) {
            throw new IllegalStateException("Closed collection case cannot be updated");
        }
        if (priority != null) {
            this.priority = priority;
        }
        this.assignedTo = assignedTo;
    }

    public void start() {
        if (status != CollectionCaseStatus.OPEN && status != CollectionCaseStatus.ON_HOLD) {
            throw new IllegalStateException("Collection case cannot be started from " + status);
        }
        status = CollectionCaseStatus.IN_PROGRESS;
    }

    public void hold() {
        if (status != CollectionCaseStatus.OPEN && status != CollectionCaseStatus.IN_PROGRESS) {
            throw new IllegalStateException("Collection case cannot be held from " + status);
        }
        status = CollectionCaseStatus.ON_HOLD;
    }

    public void close(CollectionCloseReason reason, Instant at) {
        if (status == CollectionCaseStatus.CLOSED) {
            throw new IllegalStateException("Collection case is already closed");
        }
        status = CollectionCaseStatus.CLOSED;
        closeReason = Objects.requireNonNull(reason);
        closedAt = Objects.requireNonNull(at);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getInvoiceId() { return invoiceId; }
    public CollectionCaseStatus getStatus() { return status; }
    public CollectionPriority getPriority() { return priority; }
    public UUID getAssignedTo() { return assignedTo; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public CollectionCloseReason getCloseReason() { return closeReason; }
}
