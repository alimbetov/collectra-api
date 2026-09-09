package io.collectra.api.importing.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "source_fields")
public class SourceField {
    @Id private UUID id;

    @Column(name = "source_schema_id", nullable = false)
    private UUID sourceSchemaId;

    @Column(name = "source_path", nullable = false, length = 300)
    private String sourcePath;

    @Column(name = "detected_type", length = 30)
    private String detectedType;

    @Column(name = "sample_value", length = 500)
    private String sampleValue;

    @Column(nullable = false)
    private boolean required;

    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SourceFieldScope scope;

    @Column(name = "document_key", nullable = false)
    private boolean documentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_policy", nullable = false, length = 30)
    private FieldValuePolicy valuePolicy;

    protected SourceField() {}

    public SourceField(
            UUID sourceSchemaId,
            String sourcePath,
            String detectedType,
            String sampleValue,
            boolean required,
            Integer position) {
        this(sourceSchemaId, sourcePath, detectedType, sampleValue, required, position,
                SourceFieldScope.DOCUMENT, false, FieldValuePolicy.FIRST_NON_EMPTY);
    }

    public SourceField(UUID sourceSchemaId, String sourcePath, String detectedType,
            String sampleValue, boolean required, Integer position, SourceFieldScope scope,
            boolean documentKey, FieldValuePolicy valuePolicy) {
        this.id = UUID.randomUUID();
        this.sourceSchemaId = sourceSchemaId;
        this.sourcePath = sourcePath;
        this.detectedType = detectedType;
        this.sampleValue = sampleValue;
        this.required = required;
        this.position = position;
        this.scope = scope;
        this.documentKey = documentKey;
        this.valuePolicy = valuePolicy;
    }

    public UUID getId() {
        return id;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public UUID getSourceSchemaId() { return sourceSchemaId; }
    public String getDetectedType() { return detectedType; }
    public String getSampleValue() { return sampleValue; }
    public Integer getPosition() { return position; }
    public SourceFieldScope getScope() { return scope; }
    public boolean isDocumentKey() { return documentKey; }
    public FieldValuePolicy getValuePolicy() { return valuePolicy; }

    public boolean isRequired() {
        return required;
    }

    public void update(String sourcePath, String detectedType, String sampleValue,
            boolean required, Integer position) {
        update(sourcePath, detectedType, sampleValue, required, position, scope, documentKey,
                valuePolicy);
    }

    public void update(String sourcePath, String detectedType, String sampleValue,
            boolean required, Integer position, SourceFieldScope scope, boolean documentKey,
            FieldValuePolicy valuePolicy) {
        this.sourcePath = sourcePath;
        this.detectedType = detectedType;
        this.sampleValue = sampleValue;
        this.required = required;
        this.position = position;
        this.scope = scope;
        this.documentKey = documentKey;
        this.valuePolicy = valuePolicy;
    }
}
