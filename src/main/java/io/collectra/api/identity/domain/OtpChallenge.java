package io.collectra.api.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_type", nullable = false) private String subjectType;
    @Column(name = "subject_id", nullable = false) private UUID subjectId;
    @Column(nullable = false) private String purpose;
    @Column(name = "code_hash", nullable = false) private String codeHash;
    @Column(nullable = false) private int attempts;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected OtpChallenge() {}
    public OtpChallenge(UUID tenantId, String subjectType, UUID subjectId, String purpose, String hash) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.subjectType = subjectType;
        this.subjectId = subjectId; this.purpose = purpose; this.codeHash = hash;
        this.expiresAt = Instant.now().plusSeconds(300); this.createdAt = Instant.now();
    }
    public boolean verify(String hash) {
        if (consumedAt != null || expiresAt.isBefore(Instant.now()) || attempts >= 5) return false;
        attempts++; if (java.security.MessageDigest.isEqual(codeHash.getBytes(), hash.getBytes())) { consumedAt = Instant.now(); return true; }
        return false;
    }
    public UUID getId() { return id; }
}
