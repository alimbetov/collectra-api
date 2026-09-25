package io.collectra.api.integration.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "integration_sources")
public class IntegrationSource extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private IntegrationSourceStatus status;
    @Column(name = "service_client_id", nullable = false) private UUID serviceClientId;
    @Column(name = "source_schema_definition_id", nullable = false) private UUID sourceSchemaDefinitionId;
    @Column(name = "mapping_profile_definition_id", nullable = false) private UUID mappingProfileDefinitionId;
    @Column(name = "processing_mode", nullable = false, length = 30) private String processingMode;
    @Column(name = "header_mapping", nullable = false, columnDefinition = "jsonb") private String headerMapping;
    @Column(name = "resource_policy", nullable = false, columnDefinition = "jsonb") private String resourcePolicy;
    @Column(name = "routing_config", nullable = false, columnDefinition = "jsonb") private String routingConfig;

    protected IntegrationSource() {}

    public IntegrationSource(UUID tenantId, String code, String name, UUID serviceClientId,
            UUID sourceSchemaDefinitionId, UUID mappingProfileDefinitionId, String processingMode,
            String headerMapping, String resourcePolicy, String routingConfig) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.serviceClientId = serviceClientId;
        this.sourceSchemaDefinitionId = sourceSchemaDefinitionId;
        this.mappingProfileDefinitionId = mappingProfileDefinitionId;
        this.processingMode = processingMode;
        this.headerMapping = headerMapping;
        this.resourcePolicy = resourcePolicy;
        this.routingConfig = routingConfig;
        this.status = IntegrationSourceStatus.DRAFT;
    }

    public void update(String name, UUID serviceClientId, UUID sourceSchemaDefinitionId,
            UUID mappingProfileDefinitionId, String processingMode, String headerMapping,
            String resourcePolicy, String routingConfig) {
        if (status == IntegrationSourceStatus.ARCHIVED) throw new IllegalStateException("Archived source cannot be changed");
        this.name = name; this.serviceClientId = serviceClientId;
        this.sourceSchemaDefinitionId = sourceSchemaDefinitionId;
        this.mappingProfileDefinitionId = mappingProfileDefinitionId;
        this.processingMode = processingMode; this.headerMapping = headerMapping;
        this.resourcePolicy = resourcePolicy; this.routingConfig = routingConfig;
    }
    public void activate() { if (status != IntegrationSourceStatus.DRAFT && status != IntegrationSourceStatus.SUSPENDED) throw new IllegalStateException("Only draft or suspended source can be activated"); status = IntegrationSourceStatus.ACTIVE; }
    public void suspend() { if (status != IntegrationSourceStatus.ACTIVE) throw new IllegalStateException("Only active source can be suspended"); status = IntegrationSourceStatus.SUSPENDED; }
    public void archive() { if (status == IntegrationSourceStatus.ARCHIVED) throw new IllegalStateException("Source is already archived"); status = IntegrationSourceStatus.ARCHIVED; }

    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public String getCode(){return code;}
    public String getName(){return name;} public IntegrationSourceStatus getStatus(){return status;}
    public UUID getServiceClientId(){return serviceClientId;} public UUID getSourceSchemaDefinitionId(){return sourceSchemaDefinitionId;}
    public UUID getMappingProfileDefinitionId(){return mappingProfileDefinitionId;} public String getProcessingMode(){return processingMode;}
    public String getHeaderMapping(){return headerMapping;} public String getResourcePolicy(){return resourcePolicy;} public String getRoutingConfig(){return routingConfig;}
}
