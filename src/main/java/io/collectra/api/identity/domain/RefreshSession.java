package io.collectra.api.identity.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="refresh_sessions", indexes=@Index(name="idx_refresh_token_hash",columnList="token_hash",unique=true))
public class RefreshSession extends AuditableEntity {
    @Id private UUID id;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(name="family_id",nullable=false) private UUID familyId;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    protected RefreshSession() {}
    public RefreshSession(UUID userId,UUID tenantId,String hash,UUID familyId,Instant expiresAt){this.id=UUID.randomUUID();this.userId=userId;this.tenantId=tenantId;this.tokenHash=hash;this.familyId=familyId;this.expiresAt=expiresAt;}
    public boolean active(){return revokedAt==null && expiresAt.isAfter(Instant.now());} public boolean revoked(){return revokedAt!=null;} public void revoke(){if(revokedAt==null)revokedAt=Instant.now();}
    public UUID getUserId(){return userId;} public UUID getFamilyId(){return familyId;} public UUID getTenantId(){return tenantId;}
}
