package io.collectra.api.integration.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_client_credentials")
public class ServiceClientCredential extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "service_client_id", nullable = false)
    private UUID serviceClientId;

    @Column(name = "secret_hash", nullable = false, length = 100)
    private String secretHash;

    @Column(name = "secret_hint", nullable = false, length = 20)
    private String secretHint;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ServiceClientCredential() {}

    public ServiceClientCredential(
            UUID serviceClientId,
            String secretHash,
            String secretHint,
            String status,
            Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.serviceClientId = serviceClientId;
        this.secretHash = secretHash;
        this.secretHint = secretHint;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public boolean usableAt(Instant now) {
        return ("ACTIVE".equals(status) || "ROTATING".equals(status))
                && revokedAt == null
                && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void activate() {
        status = "ACTIVE";
    }

    public void revoke(Instant now) {
        status = "REVOKED";
        revokedAt = now;
    }

    public void usedAt(Instant now) {
        lastUsedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceClientId() {
        return serviceClientId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
