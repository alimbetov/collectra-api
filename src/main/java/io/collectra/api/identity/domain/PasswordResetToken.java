package io.collectra.api.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PasswordResetToken() {}
    public PasswordResetToken(UUID userId, UUID tenantId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.userId = userId; this.tenantId = tenantId;
        this.tokenHash = tokenHash; this.expiresAt = expiresAt; this.createdAt = Instant.now();
    }
    public boolean activeAt(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }
    public void consume(Instant now) { consumedAt = now; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
}
