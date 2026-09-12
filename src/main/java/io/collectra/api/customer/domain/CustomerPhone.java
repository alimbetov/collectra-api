package io.collectra.api.customer.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "customer_phones",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_customer_phone",
                        columnNames = {"tenant_id", "customer_id", "normalized_phone"}))
public class CustomerPhone extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "normalized_phone", nullable = false, length = 24)
    private String normalizedPhone;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false, length = 20)
    private String status;

    protected CustomerPhone() {}

    public CustomerPhone(
            UUID tenantId, UUID customerId, String phone, String type, boolean primary) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = Objects.requireNonNull(customerId);
        this.phone = required(phone);
        this.normalizedPhone = normalizeForLookup(phone);
        this.type = normalizeType(type);
        this.primary = primary;
        this.verified = false;
        this.status = "ACTIVE";
    }

    public void updateMetadata(String type, Boolean primary, String status) {
        if (type != null) {
            this.type = normalizeType(type);
        }
        if (status != null) {
            String normalizedStatus = normalizeStatus(status);
            this.status = normalizedStatus;
            if ("INACTIVE".equals(normalizedStatus)) {
                this.primary = false;
            }
        }
        if (primary != null) {
            if (primary && !isActive()) {
                throw new IllegalArgumentException("Inactive phone cannot be primary");
            }
            this.primary = primary;
        }
    }

    public void demotePrimary() {
        this.primary = false;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getPhone() {
        return phone;
    }

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public String getType() {
        return type;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getStatus() {
        return status;
    }

    public static String normalizeForLookup(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7 || digits.length() > 15) {
            throw new IllegalArgumentException("Invalid phone");
        }
        return "+" + digits;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }
        return value.trim();
    }

    private static String normalizeType(String value) {
        return value == null || value.isBlank() ? "OTHER" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeStatus(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported phone status");
        }
        return normalized;
    }
}
