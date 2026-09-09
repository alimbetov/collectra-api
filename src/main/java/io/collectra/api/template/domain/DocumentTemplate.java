package io.collectra.api.template.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "document_templates")
public class DocumentTemplate extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "document_type", nullable = false, length = 50) private String documentType;
    @Column(nullable = false, length = 20) private String status;
    protected DocumentTemplate() {}
    public DocumentTemplate(UUID tenantId, String code, String name, String documentType) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.code = code;
        this.name = name; this.documentType = documentType; this.status = "ACTIVE";
    }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDocumentType() { return documentType; }
    public String getStatus() { return status; }
    public void rename(String name) {
        if (!"ACTIVE".equals(status)) throw new IllegalStateException("Archived template cannot be changed");
        this.name = name;
    }
    public void archive() { this.status = "ARCHIVED"; }
}
