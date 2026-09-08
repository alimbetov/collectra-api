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

    protected SourceField() {}

    public SourceField(
            UUID sourceSchemaId,
            String sourcePath,
            String detectedType,
            String sampleValue,
            boolean required,
            Integer position) {
        this.id = UUID.randomUUID();
        this.sourceSchemaId = sourceSchemaId;
        this.sourcePath = sourcePath;
        this.detectedType = detectedType;
        this.sampleValue = sampleValue;
        this.required = required;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public boolean isRequired() {
        return required;
    }
}
