package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.file.infrastructure.storage.*;
import io.collectra.api.importing.domain.*;
import io.collectra.api.importing.infrastructure.*;
import io.collectra.api.integration.application.*;
import io.collectra.api.integration.domain.*;
import io.collectra.api.integration.infrastructure.*;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.template.domain.FieldDefinition;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Import(ProductionIngestionAcceptanceTest.StorageConfig.class)
class ProductionIngestionAcceptanceTest extends AbstractIntegrationTest {
    static final GenericContainer<?> RABBIT = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-alpine")).withExposedPorts(5672);
    static { RABBIT.start(); }
    @DynamicPropertySource static void rabbit(DynamicPropertyRegistry r){
        r.add("spring.rabbitmq.host",RABBIT::getHost);r.add("spring.rabbitmq.port",()->RABBIT.getMappedPort(5672));
        r.add("collectra.messaging.outbox-publish-delay-ms",()->"100");
    }

    @Autowired TenantRepository tenants; @Autowired ServiceClientRepository clients;
    @Autowired SourceSchemaDefinitionRepository schemaDefinitions; @Autowired SourceSchemaRepository schemas;
    @Autowired SourceFieldRepository sourceFields; @Autowired MappingProfileDefinitionRepository mappingDefinitions;
    @Autowired MappingProfileRepository mappings; @Autowired MappingRuleRepository rules;
    @Autowired FieldDefinitionRepository fields; @Autowired IntegrationSourceRepository sources;
    @Autowired IngestionApplicationService ingestion; @Autowired IngestionBatchRepository batches;
    @Autowired IngestionRecordDiagnosticRepository diagnostics; @Autowired ReceivableService receivables;
    @Autowired ObjectMapper json;

    @Test
    void durableHappyPathAndTransportReplayReachCanonicalInvoice() throws Exception {
        Fixture f=fixture("happy");
        byte[] body=csv("INV-1","100.00");
        var first=ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"idem-1","req-1","text/csv",body);
        var replay=ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"idem-1","req-2","text/csv",body);
        assertThat(replay.ingestionId()).isEqualTo(first.ingestionId());assertThat(replay.replayed()).isTrue();
        awaitTerminal(f.tenantId,first.ingestionId());
        var batch=batches.findByIdAndTenantId(first.ingestionId(),f.tenantId).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(batch.getCreatedCount()).isEqualTo(1);
        assertThat(receivables.findInvoiceByExternalId(f.tenantId,"INV-1")).isPresent();
        assertThat(diagnostics.findByTenantIdAndBatchIdAndRecordOrder(f.tenantId,first.ingestionId(),1).orElseThrow().getOutcome()).isEqualTo("CREATED");
    }

    @Test
    void businessReplayIsReusedAndChangedCanonicalStateIsConflict() throws Exception {
        Fixture f=fixture("business");
        var one=ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"k1",null,"text/csv",csv("INV-X","100.00"));awaitTerminal(f.tenantId,one.ingestionId());
        var same=ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"k2",null,"text/csv",csv("INV-X","100.00"));awaitTerminal(f.tenantId,same.ingestionId());
        assertThat(diagnostics.findByTenantIdAndBatchIdAndRecordOrder(f.tenantId,same.ingestionId(),1).orElseThrow().getOutcome()).isEqualTo("REUSED");
        var changed=ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"k3",null,"text/csv",csv("INV-X","200.00"));awaitTerminal(f.tenantId,changed.ingestionId());
        assertThat(diagnostics.findByTenantIdAndBatchIdAndRecordOrder(f.tenantId,changed.ingestionId(),1).orElseThrow().getOutcome()).isEqualTo("CONFLICT");
    }

    @Test
    void concurrentSameTransportKeyProducesOneBatch() throws Exception {
        Fixture f=fixture("race");byte[] body=csv("INV-R","100.00");ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<UUID> call=()->{ready.countDown();start.await(5,TimeUnit.SECONDS);return ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"race-key",null,"text/csv",body).ingestionId();};
        try{Future<UUID>a=pool.submit(call),b=pool.submit(call);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
            assertThat(batches.findAll().stream().filter(x->x.getTenantId().equals(f.tenantId)&&x.getIdempotencyKey().equals("race-key"))).hasSize(1);
        }finally{pool.shutdownNow();}
    }

    @Test
    void sameTransportKeyWithDifferentBodyConflicts() {
        Fixture f=fixture("hash");ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"hash-key",null,"text/csv",csv("INV-H","100.00"));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->ingestion.reserve(f.tenantId,f.clientId,f.sourceCode,"hash-key",null,"text/csv",csv("INV-H","200.00")))
                .isInstanceOf(IngestionConflictException.class).hasMessageContaining("different request body");
    }

    private void awaitTerminal(UUID tenantId,UUID id) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<deadline){var b=batches.findByIdAndTenantId(id,tenantId).orElseThrow();
            if(Set.of(IngestionStatus.COMPLETED,IngestionStatus.PARTIALLY_COMPLETED,IngestionStatus.FAILED).contains(b.getStatus()))return;
            Thread.sleep(100);}
        throw new AssertionError("ingestion did not become terminal");
    }

    private Fixture fixture(String prefix){
        Tenant tenant=tenants.saveAndFlush(new Tenant("vc3-"+prefix+"-"+UUID.randomUUID(),"VC3"));
        ServiceClient client=clients.saveAndFlush(new ServiceClient(tenant.getId(),"svc-"+UUID.randomUUID(),"VC3",Set.of("integration:imports:create","integration:imports:read"),null));
        SourceSchemaDefinition sd=schemaDefinitions.saveAndFlush(new SourceSchemaDefinition(tenant.getId(),"S"+UUID.randomUUID().toString().replace("-","").substring(0,8),"Schema"));
        SourceSchema schema=schemas.saveAndFlush(new SourceSchema(tenant.getId(),sd.getId(),"Schema",SourceFormat.CSV,1));
        SourceField invoice=field(schema,"Invoice",1,true),customer=field(schema,"Customer",2,false),number=field(schema,"Number",3,false),due=field(schema,"DueDate",4,false),amount=field(schema,"Amount",5,false),currency=field(schema,"Currency",6,false);
        schema.validated();schema.publish();schemas.saveAndFlush(schema);
        MappingProfileDefinition md=mappingDefinitions.saveAndFlush(new MappingProfileDefinition(tenant.getId(),"M"+UUID.randomUUID().toString().replace("-","").substring(0,8),"Mapping","INVOICE"));
        MappingProfile mapping=mappings.saveAndFlush(new MappingProfile(tenant.getId(),md.getId(),schema.getId(),"Mapping","INVOICE",1));
        mapping.validated();mapping.publish();mappings.saveAndFlush(mapping);
        rule(mapping,invoice,"invoice.externalId","TRIM");rule(mapping,customer,"customer.externalId","TRIM");rule(mapping,number,"invoice.invoiceNumber","TRIM");rule(mapping,due,"invoice.dueDate","DATE_PARSE");rule(mapping,amount,"invoice.amount","DECIMAL_PARSE");rule(mapping,currency,"invoice.currency","TRIM");
        String sourceCode="src-"+UUID.randomUUID().toString().substring(0,8);
        IntegrationSource source=new IntegrationSource(tenant.getId(),sourceCode,"Source",client.getId(),sd.getId(),md.getId(),"STANDARD","{}","{}","{}");source.activate();sources.saveAndFlush(source);
        return new Fixture(tenant.getId(),client.getId(),sourceCode);
    }
    private SourceField field(SourceSchema s,String path,int pos,boolean key){return sourceFields.saveAndFlush(new SourceField(s.getId(),path,"STRING",null,true,pos,SourceFieldScope.DOCUMENT,key,FieldValuePolicy.REQUIRE_SAME));}
    private void rule(MappingProfile p,SourceField s,String targetKey,String transform){FieldDefinition target=fields.findById(targetId(targetKey)).orElseThrow();var cfg=json.createObjectNode().put("type",transform);if(transform.equals("DATE_PARSE"))cfg.put("pattern","yyyy-MM-dd");if(transform.equals("DECIMAL_PARSE"))cfg.put("decimalSeparator",".");rules.saveAndFlush(new MappingRule(p.getId(),s.getId(),target.getId(),cfg,null,true));}
    private UUID targetId(String key){return UUID.fromString(switch(key){case "customer.externalId"->"20000000-0000-0000-0000-000000000010";case "invoice.externalId"->"20000000-0000-0000-0000-000000000020";case "invoice.invoiceNumber"->"20000000-0000-0000-0000-000000000021";case "invoice.dueDate"->"20000000-0000-0000-0000-000000000023";case "invoice.amount"->"20000000-0000-0000-0000-000000000024";case "invoice.currency"->"20000000-0000-0000-0000-000000000025";default->throw new IllegalArgumentException(key);});}
    private byte[] csv(String invoice,String amount){return ("Invoice;Customer;Number;DueDate;Amount;Currency\n"+invoice+";CUST-1;N-1;2026-09-01;"+amount+";KZT\n").getBytes(StandardCharsets.UTF_8);}
    private record Fixture(UUID tenantId,UUID clientId,String sourceCode){}

    @TestConfiguration
    static class StorageConfig {
        @Bean @Primary ObjectStorage ingestionTestStorage(){return new MemoryStorage();}
    }
    static class MemoryStorage implements ObjectStorage {
        private final ConcurrentMap<String,byte[]> values=new ConcurrentHashMap<>();
        private String key(StorageLocation l){return l.bucket()+"/"+l.objectKey();}
        public StoredObject upload(UploadObject c){try{byte[] b=c.content().readAllBytes();values.put(key(c.location()),b);return new StoredObject(b.length,"test");}catch(IOException e){throw new RuntimeException(e);}}
        public InputStream download(StorageLocation l){return new ByteArrayInputStream(Objects.requireNonNull(values.get(key(l))));}
        public ObjectMetadata stat(StorageLocation l){byte[] b=Objects.requireNonNull(values.get(key(l)));return new ObjectMetadata(b.length,"application/octet-stream","test");}
        public void delete(StorageLocation l){values.remove(key(l));} public boolean exists(StorageLocation l){return values.containsKey(key(l));}
        public URI generatePresignedGetUrl(StorageLocation l,Duration ttl){return URI.create("https://test.invalid/get");}
        public URI generatePresignedPutUrl(StorageLocation l,String type,Duration ttl){return URI.create("https://test.invalid/put");}
    }
}
