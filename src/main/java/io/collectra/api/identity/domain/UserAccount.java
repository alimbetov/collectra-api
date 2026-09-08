package io.collectra.api.identity.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="user_accounts", uniqueConstraints=@UniqueConstraint(name="uk_user_tenant_email", columnNames={"tenant_id","email"}))
public class UserAccount extends AuditableEntity {
    @Id private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(nullable=false, length=254) private String email;
    @Column(name = "display_name", length = 200) private String displayName;
    @Column(length = 10) private String locale;
    @Column(length = 60) private String timezone;
    @Column(name = "email_verified_at") private java.time.Instant emailVerifiedAt;
    @Column(name="password_hash", nullable=false, length=100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private SystemRole role;
    @Column(nullable=false, length=20) private String status;
    @Column(name = "authorization_version", nullable = false)
    private long authorizationVersion;
    protected UserAccount() {}
    public UserAccount(UUID tenantId,String email,String passwordHash,SystemRole role){this.id=UUID.randomUUID();this.tenantId=tenantId;this.email=email.toLowerCase();this.passwordHash=passwordHash;this.role=role;this.status="ACTIVE";}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public String getEmail(){return email;}
    public String getPasswordHash(){return passwordHash;} public SystemRole getRole(){return role;} public String getStatus(){return status;}
    public long getAuthorizationVersion() { return authorizationVersion; }
    public String getDisplayName() { return displayName; }
    public String getLocale() { return locale; }
    public String getTimezone() { return timezone; }
    public void completeProfile(String displayName, String locale, String timezone) {
        this.displayName = displayName;
        this.locale = locale;
        this.timezone = timezone;
        this.emailVerifiedAt = java.time.Instant.now();
    }
    public void updateProfile(String displayName, String locale, String timezone) {
        this.displayName = displayName;
        this.locale = locale;
        this.timezone = timezone;
    }
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        authorizationVersion++;
    }
    public void block() { status = "BLOCKED"; authorizationVersion++; }
    public void activate() { status = "ACTIVE"; authorizationVersion++; }
    public void authorizationChanged() { authorizationVersion++; }
}
