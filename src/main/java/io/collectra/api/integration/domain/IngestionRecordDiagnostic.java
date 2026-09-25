package io.collectra.api.integration.domain;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="ingestion_record_diagnostics")
public class IngestionRecordDiagnostic {
 @Id private UUID id;@Column(name="tenant_id",nullable=false)private UUID tenantId;@Column(name="ingestion_batch_id",nullable=false)private UUID batchId;@Column(name="record_order",nullable=false)private int recordOrder;private String documentKey;private String status;private String stage;private String targetType;private UUID targetId;private String externalId;private String errorCode;private String safeErrorMessage;private String fieldPath;@Column(name="created_at",nullable=false)private Instant createdAt;
 protected IngestionRecordDiagnostic(){}
 public IngestionRecordDiagnostic(UUID tenantId,UUID batchId,int order,String key,String status,String stage,String type,UUID targetId,String externalId,String code,String message,Instant at){this.id=UUID.randomUUID();this.tenantId=tenantId;this.batchId=batchId;this.recordOrder=order;this.documentKey=key;this.status=status;this.stage=stage;this.targetType=type;this.targetId=targetId;this.externalId=externalId;this.errorCode=code;this.safeErrorMessage=message;this.createdAt=at;}
}
