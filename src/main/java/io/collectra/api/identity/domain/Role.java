package io.collectra.api.identity.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role extends AuditableEntity {

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(nullable = false, length = 80) private String code;
    @Column(name = "scope_type", nullable = false, length = 20) private String scopeType;
    @Column(name = "system_role", nullable = false) private boolean systemRole;

    protected Role() {}

    public Role(UUID tenantId, String code) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.code = code;
        this.scopeType = "TENANT";
        this.systemRole = false;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getScopeType() { return scopeType; }
    public boolean isSystemRole() { return systemRole; }
    public void rename(String code) { this.code = code; }
}
