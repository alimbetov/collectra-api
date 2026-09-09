package io.collectra.api.importing.domain;

import io.collectra.api.shared.persistence.AuditableEntity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "source_schemas")
public class SourceSchema extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "definition_id")
    private UUID definitionId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 20)
    private SourceFormat sourceFormat;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DefinitionStatus status;

    protected SourceSchema() {}

    public SourceSchema(
            UUID tenantId, String code, String name, SourceFormat format, int schemaVersion) {
        this(tenantId, null, code, name, format, schemaVersion);
    }

    public SourceSchema(
            UUID tenantId, UUID definitionId, String code, String name, SourceFormat format, int schemaVersion) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.definitionId = definitionId;
        this.code = code;
        this.name = name;
        this.sourceFormat = format;
        this.schemaVersion = schemaVersion;
        this.status = DefinitionStatus.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDefinitionId() { return definitionId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getSchemaVersion() { return schemaVersion; }

    public SourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public DefinitionStatus getStatus() {
        return status;
    }

    public void publish() {
        if (status != DefinitionStatus.VALIDATED)
            throw new IllegalStateException("Only validated schema can be published");
        status = DefinitionStatus.PUBLISHED;
    }

    public void validated() {
        if (status != DefinitionStatus.DRAFT)
            throw new IllegalStateException("Only draft schema can be validated");
        status = DefinitionStatus.VALIDATED;
    }

    public void reopen() {
        if (status != DefinitionStatus.VALIDATED)
            throw new IllegalStateException("Only validated schema can return to draft");
        status = DefinitionStatus.DRAFT;
    }

    public void archive() {
        if (status != DefinitionStatus.PUBLISHED)
            throw new IllegalStateException("Only published schema can be archived");
        status = DefinitionStatus.ARCHIVED;
    }
}
