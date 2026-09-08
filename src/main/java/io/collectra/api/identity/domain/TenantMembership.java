package io.collectra.api.identity.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_memberships")
public class TenantMembership extends AuditableEntity {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected TenantMembership() {}

    public TenantMembership(UUID tenantId, UUID userId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.status = "ACTIVE";
        this.joinedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public boolean active() { return "ACTIVE".equals(status); }
    public void block() { status = "BLOCKED"; }
    public void activate() { status = "ACTIVE"; }
}
