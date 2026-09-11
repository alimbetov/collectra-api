package io.collectra.api.customer.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "customer_emails", uniqueConstraints = @UniqueConstraint(name = "uk_customer_email", columnNames = {"tenant_id", "customer_id", "email"}))
public class CustomerEmail extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(nullable = false, length = 320) private String email;
    @Column(nullable = false, length = 20) private String type;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(nullable = false) private boolean verified;
    @Column(nullable = false, length = 20) private String status;

    protected CustomerEmail() {}
    public CustomerEmail(UUID tenantId, UUID customerId, String email, String type, boolean primary) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.customerId = customerId;
        this.email = normalize(email); this.type = type == null || type.isBlank() ? "OTHER" : type.trim().toUpperCase(Locale.ROOT);
        this.primary = primary; this.verified = false; this.status = "ACTIVE";
    }
    public UUID getId() { return id; } public UUID getCustomerId() { return customerId; }
    public String getEmail() { return email; } public String getType() { return type; }
    public boolean isPrimary() { return primary; } public boolean isVerified() { return verified; }
    public String getStatus() { return status; }
    private static String normalize(String value) { if (value == null || value.isBlank() || !value.contains("@")) throw new IllegalArgumentException("Valid email is required"); return value.trim().toLowerCase(Locale.ROOT); }
}
