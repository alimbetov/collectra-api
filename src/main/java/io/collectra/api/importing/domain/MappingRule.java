package io.collectra.api.importing.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "mapping_rules")
public class MappingRule {
    @Id private UUID id;
    @Column(name = "mapping_profile_id", nullable = false) private UUID mappingProfileId;
    @Column(name = "source_field_id", nullable = false) private UUID sourceFieldId;
    @Column(name = "target_field_id", nullable = false) private UUID targetFieldId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode transformation;
    @Column(name = "default_value", length = 500) private String defaultValue;
    @Column(nullable = false) private boolean required;
    protected MappingRule() {}
    public MappingRule(UUID mappingProfileId, UUID sourceFieldId, UUID targetFieldId,
            JsonNode transformation, String defaultValue, boolean required) {
        this.id = UUID.randomUUID(); this.mappingProfileId = mappingProfileId;
        this.sourceFieldId = sourceFieldId; this.targetFieldId = targetFieldId;
        this.transformation = transformation; this.defaultValue = defaultValue; this.required = required;
    }
    public UUID getId() { return id; } public UUID getTargetFieldId() { return targetFieldId; }
}
