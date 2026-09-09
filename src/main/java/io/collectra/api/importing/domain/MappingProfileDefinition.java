package io.collectra.api.importing.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "mapping_profile_definitions")
public class MappingProfileDefinition extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    protected MappingProfileDefinition() {}

    public MappingProfileDefinition(UUID tenantId, String code, String name, String documentType) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.documentType = documentType;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDocumentType() { return documentType; }
    public void rename(String name) { this.name = name; }
}
