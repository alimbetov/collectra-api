package io.collectra.api.identity.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
        name = "user_accounts",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_user_tenant_email",
                        columnNames = {"tenant_id", "email"}))
public class UserAccount extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SystemRole role;

    @Column(nullable = false, length = 20)
    private String status;

    protected UserAccount() {}

    public UserAccount(UUID tenantId, String email, String passwordHash, SystemRole role) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = "ACTIVE";
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public SystemRole getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }
}
