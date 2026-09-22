package io.collectra.api.customer.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "customer_channel_addresses",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_customer_channel_address",
                        columnNames = {"tenant_id", "customer_id", "channel", "address"}))
public class CustomerChannelAddress extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected CustomerChannelAddress() {}

    public CustomerChannelAddress(
            UUID tenantId,
            UUID customerId,
            String channel,
            String address,
            boolean primary,
            Instant verifiedAt) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.customerId = Objects.requireNonNull(customerId, "customerId is required");
        this.channel = required(channel, "channel").toUpperCase(Locale.ROOT);
        this.address = required(address, "address");
        this.primary = primary;
        this.status = "ACTIVE";
        this.verifiedAt = verifiedAt;
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

    public String getChannel() {
        return channel;
    }

    public String getAddress() {
        return address;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getStatus() {
        return status;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
