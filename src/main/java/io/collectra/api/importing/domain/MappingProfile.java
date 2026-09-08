package io.collectra.api.importing.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "mapping_profiles")
public class MappingProfile extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "source_schema_id", nullable = false) private UUID sourceSchemaId;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "document_type", nullable = false, length = 50) private String documentType;
    @Column(name = "profile_version", nullable = false) private int profileVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DefinitionStatus status;
    protected MappingProfile() {}
    public MappingProfile(UUID tenantId, UUID sourceSchemaId, String code, String name,
            String documentType, int profileVersion) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.sourceSchemaId = sourceSchemaId;
        this.code = code; this.name = name; this.documentType = documentType;
        this.profileVersion = profileVersion; this.status = DefinitionStatus.DRAFT;
    }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; }
    public DefinitionStatus getStatus() { return status; }
}
