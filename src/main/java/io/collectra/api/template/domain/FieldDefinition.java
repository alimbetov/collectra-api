package io.collectra.api.template.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "field_definitions")
public class FieldDefinition extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "field_key", nullable = false, length = 160) private String key;
    @Column(nullable = false, length = 200) private String label;
    @Enumerated(EnumType.STRING) @Column(name = "data_type", nullable = false, length = 30)
    private FieldDataType dataType;
    @Column(nullable = false, length = 60) private String category;
    @Column(nullable = false) private boolean collection;
    @Column(nullable = false) private boolean required;
    @Column(length = 500) private String description;
    @Column(name = "example_value", length = 500) private String exampleValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "validation_rules", nullable = false, columnDefinition = "jsonb")
    private JsonNode validationRules;
    @Column(nullable = false, length = 20) private String status;
    protected FieldDefinition() {}
    public FieldDefinition(UUID tenantId, String key, String label, FieldDataType dataType,
            String category, boolean collection, boolean required, JsonNode validationRules) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.key = key; this.label = label;
        this.dataType = dataType; this.category = category; this.collection = collection;
        this.required = required; this.validationRules = validationRules; this.status = "ACTIVE";
    }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; }
    public String getKey() { return key; } public FieldDataType getDataType() { return dataType; }
    public String getStatus() { return status; }
}
