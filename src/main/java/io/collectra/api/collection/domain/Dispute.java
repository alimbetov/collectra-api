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
@Table(name = "collection_disputes")
public class Dispute extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false, length = 80)
    private String reason;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeStatus status;

    @Column(name = "resolution_code", length = 80)
    private String resolutionCode;

    @Column(name = "resolution_summary", length = 1000)
    private String resolutionSummary;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    protected Dispute() {}

    public Dispute(UUID tenantId, UUID caseId, String reason, String description) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.caseId = Objects.requireNonNull(caseId);
        this.reason = required(reason, "reason");
        this.description = trim(description);
        this.status = DisputeStatus.OPEN;
    }

    public void resolve(String code, String summary, String actor, Instant at) {
        requireOpen();
        this.status = DisputeStatus.RESOLVED;
        this.resolutionCode = required(code, "resolutionCode");
        this.resolutionSummary = trim(summary);
        this.resolvedBy = trim(actor);
        this.resolvedAt = Objects.requireNonNull(at);
    }

    public void cancel(String actor, Instant at) {
        requireOpen();
        this.status = DisputeStatus.CANCELLED;
        this.resolvedBy = trim(actor);
        this.resolvedAt = Objects.requireNonNull(at);
    }

    private void requireOpen() {
        if (status != DisputeStatus.OPEN) {
            throw new IllegalStateException("Dispute is already terminal");
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCaseId() { return caseId; }
    public String getReason() { return reason; }
    public String getDescription() { return description; }
    public DisputeStatus getStatus() { return status; }
    public String getResolutionCode() { return resolutionCode; }
    public String getResolutionSummary() { return resolutionSummary; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
