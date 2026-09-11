package io.collectra.api.customer.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "customer_segment_members", uniqueConstraints = @UniqueConstraint(name = "uk_customer_segment_member", columnNames = {"tenant_id", "customer_id", "segment_id"}))
public class CustomerSegmentMember extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "segment_id", nullable = false) private UUID segmentId;
    protected CustomerSegmentMember() {}
    public CustomerSegmentMember(UUID tenantId, UUID customerId, UUID segmentId) { this.id = UUID.randomUUID(); this.tenantId = tenantId; this.customerId = customerId; this.segmentId = segmentId; }
    public UUID getId() { return id; } public UUID getCustomerId() { return customerId; } public UUID getSegmentId() { return segmentId; }
}
