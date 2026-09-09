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

    @Column(name = "record_path", length = 300)
    private String recordPath;

    @Column(name = "row_type_field_id")
    private UUID rowTypeFieldId;

    @Column(name = "item_row_values", length = 500)
    private String itemRowValues;

    @Column(name = "total_row_values", length = 500)
    private String totalRowValues;

    @Column(name = "ignored_row_values", length = 500)
    private String ignoredRowValues;

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
    public String getRecordPath() { return recordPath; }
    public UUID getRowTypeFieldId() { return rowTypeFieldId; }
    public java.util.Set<String> getItemRowValues() { return values(itemRowValues); }
    public java.util.Set<String> getTotalRowValues() { return values(totalRowValues); }
    public java.util.Set<String> getIgnoredRowValues() { return values(ignoredRowValues); }

    public void configureRows(String recordPath, UUID rowTypeFieldId,
            java.util.Set<String> itemValues, java.util.Set<String> totalValues,
            java.util.Set<String> ignoredValues) {
        if (status != DefinitionStatus.DRAFT)
            throw new IllegalStateException("Only draft schema can be changed");
        this.recordPath = blankToNull(recordPath);
        this.rowTypeFieldId = rowTypeFieldId;
        this.itemRowValues = join(itemValues);
        this.totalRowValues = join(totalValues);
        this.ignoredRowValues = join(ignoredValues);
    }

    private String join(java.util.Set<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> v.toUpperCase(java.util.Locale.ROOT)).sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }
    private java.util.Set<String> values(String source) {
        if (source == null || source.isBlank()) return java.util.Set.of();
        return java.util.Arrays.stream(source.split(","))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

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
