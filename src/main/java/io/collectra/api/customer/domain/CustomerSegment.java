package io.collectra.api.customer.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "customer_segments", uniqueConstraints = @UniqueConstraint(name = "uk_customer_segment_code", columnNames = {"tenant_id", "code"}))
public class CustomerSegment extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 1000) private String description;
    @Column(nullable = false) private boolean active;
    protected CustomerSegment() {}
    public CustomerSegment(UUID tenantId, String code, String name, String description) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.code = required(code).toUpperCase(java.util.Locale.ROOT);
        this.name = required(name); this.description = description == null || description.isBlank() ? null : description.trim(); this.active = true;
    }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; } public String getCode() { return code; }
    public String getName() { return name; } public String getDescription() { return description; } public boolean isActive() { return active; }
    private static String required(String v) { if (v == null || v.isBlank()) throw new IllegalArgumentException("value is required"); return v.trim(); }
}
