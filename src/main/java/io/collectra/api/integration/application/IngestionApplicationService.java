package io.collectra.api.integration.application;
import com.fasterxml.jackson.databind.ObjectMapper;import io.collectra.api.file.application.*;import io.collectra.api.file.domain.FileCategory;import io.collectra.api.importing.domain.DefinitionStatus;import io.collectra.api.importing.infrastructure.*;import io.collectra.api.integration.domain.*;import io.collectra.api.integration.infrastructure.*;import io.collectra.api.shared.outbox.OutboxService;import java.io.ByteArrayInputStream;import java.security.MessageDigest;import java.time.Clock;import java.util.HexFormat;import java.util.UUID;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
public class IngestionApplicationService{
 public static final String EVENT_TYPE="INTEGRATION_INGESTION_REQUESTED";private final IntegrationSourceRepository sources;private final IngestionBatchRepository batches;private final SourceSchemaRepository schemas;private final MappingProfileRepository mappings;private final FileService files;private final OutboxService outbox;private final ObjectMapper json;private final Clock clock;
 public IngestionApplicationService(IntegrationSourceRepository sources,IngestionBatchRepository batches,SourceSchemaRepository schemas,MappingProfileRepository mappings,FileService files,OutboxService outbox,ObjectMapper json,Clock clock){this.sources=sources;this.batches=batches;this.schemas=schemas;this.mappings=mappings;this.files=files;this.outbox=outbox;this.json=json;this.clock=clock;}
 public Reservation reserve(UUID tenantId,UUID serviceClientId,String sourceCode,String key,String contentType,byte[] content){
  if(key==null||key.isBlank()||key.length()>200)throw new IllegalArgumentException("Idempotency-Key is required");
  IntegrationSource source=sources.findByTenantIdAndCode(tenantId,sourceCode).orElseThrow();
  if(source.getStatus()!=IntegrationSourceStatus.ACTIVE)throw new IngestionConflictException("SOURCE_NOT_ACTIVE","Integration source is not active");
  if(!source.getServiceClientId().equals(serviceClientId))throw new IngestionConflictException("SOURCE_CLIENT_MISMATCH","Service client is not bound to source");
  String hash=sha256(content);var existing=batches.findByTenantIdAndIntegrationSourceIdAndIdempotencyKey(tenantId,source.getId(),key);
  if(existing.isPresent()){if(!existing.get().getRequestHash().equals(hash))throw new IngestionConflictException("IDEMPOTENCY_KEY_CONFLICT","Idempotency key was already used with different content");return response(existing.get(),true);}
  UUID ingestionId=UUID.randomUUID();
  FileMetadata raw=files.upload(new UploadFileCommand(tenantId,null,FileCategory.IMPORT_SOURCE,"ingestion-"+ingestionId,contentType,content.length,new ByteArrayInputStream(content),serviceClientId));
  return persistReservation(tenantId,serviceClientId,source,key,contentType,hash,raw.id(),ingestionId);
 }
 @Transactional protected Reservation persistReservation(UUID tenantId,UUID clientId,IntegrationSource source,String key,String contentType,String hash,UUID fileId,UUID id){
  var schema=schemas.findAllByDefinitionIdOrderBySchemaVersionDesc(source.getSourceSchemaDefinitionId()).stream().filter(x->x.getTenantId().equals(tenantId)&&x.getStatus()==DefinitionStatus.PUBLISHED).findFirst().orElseThrow();
  var mapping=mappings.findAllByDefinitionIdOrderByProfileVersionDesc(source.getMappingProfileDefinitionId()).stream().filter(x->x.getTenantId().equals(tenantId)&&x.getStatus()==DefinitionStatus.PUBLISHED&&x.getSourceSchemaId().equals(schema.getId())).findFirst().orElseThrow();
  var b=new IngestionBatch(id,tenantId,source.getId(),clientId,key,hash,fileId,contentType,clock.instant(),"{}");b.queue(schema.getId(),mapping.getId(),null);batches.save(b);outbox.append(tenantId,"INGESTION",id,EVENT_TYPE,"{\"ingestionId\":\""+id+"\"}");return response(b,false);
 }
 @Transactional(readOnly=true) public Reservation status(UUID tenantId,UUID id){return response(batches.findByIdAndTenantId(id,tenantId).orElseThrow(),false);}
 private Reservation response(IngestionBatch b,boolean replayed){return new Reservation(b.getId(),b.getStatus().name(),replayed,b.getReceivedAt(),b.getRecordCount(),b.getAcceptedCount(),b.getReusedCount(),b.getFailedCount(),b.getErrorCode(),b.getSafeErrorMessage());}
 private String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
 public record Reservation(UUID ingestionId,String status,boolean replayed,java.time.Instant receivedAt,int recordCount,int acceptedCount,int reusedCount,int failedCount,String errorCode,String errorMessage){}
}
