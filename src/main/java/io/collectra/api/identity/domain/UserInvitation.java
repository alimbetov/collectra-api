package io.collectra.api.identity.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_invitations")
public class UserInvitation extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "invited_by", nullable = false) private UUID invitedBy;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_invitation_roles", joinColumns = @JoinColumn(name = "invitation_id"))
    @Column(name = "role_id")
    private Set<UUID> roleIds = new HashSet<>();

    protected UserInvitation() {}

    public UserInvitation(UUID tenantId, String email, String tokenHash, Instant expiresAt,
            UUID invitedBy, Set<UUID> roleIds) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.email = email.toLowerCase();
        this.tokenHash = tokenHash; this.expiresAt = expiresAt; this.invitedBy = invitedBy;
        this.roleIds = new HashSet<>(roleIds); this.status = "PENDING";
    }
    public boolean activeAt(Instant now) { return "PENDING".equals(status) && expiresAt.isAfter(now); }
    public void accept(Instant now) { status = "ACCEPTED"; acceptedAt = now; }
    public void revoke() { if ("PENDING".equals(status)) status = "REVOKED"; }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getInvitedBy() { return invitedBy; }
    public Set<UUID> getRoleIds() { return Set.copyOf(roleIds); }
}
