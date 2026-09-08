package io.collectra.api.importing.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "source_schemas")
public class SourceSchema extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "source_format", nullable = false, length = 20)
    private SourceFormat sourceFormat;
    @Column(name = "schema_version", nullable = false) private int schemaVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DefinitionStatus status;
    protected SourceSchema() {}
    public SourceSchema(UUID tenantId, String code, String name, SourceFormat format, int schemaVersion) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.code = code; this.name = name;
        this.sourceFormat = format; this.schemaVersion = schemaVersion; this.status = DefinitionStatus.DRAFT;
    }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; }
    public DefinitionStatus getStatus() { return status; }
    public void publish() { if (status != DefinitionStatus.DRAFT) throw new IllegalStateException("Only draft schema can be published"); status = DefinitionStatus.PUBLISHED; }
}
