package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.importing.domain.*;
import io.collectra.api.importing.infrastructure.*;
import io.collectra.api.integration.application.IntegrationSourceService;
import io.collectra.api.integration.domain.IntegrationSourceStatus;
import io.collectra.api.integration.infrastructure.IntegrationSourceRepository;
import io.collectra.api.integration.infrastructure.ServiceClientRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IntegrationSourceLifecycleIntegrationTest extends AbstractIntegrationTest {
    @Autowired TenantRepository tenants;
    @Autowired ServiceClientRepository clients;
    @Autowired SourceSchemaDefinitionRepository schemaDefinitions;
    @Autowired SourceSchemaRepository schemas;
    @Autowired MappingProfileDefinitionRepository mappingDefinitions;
    @Autowired MappingProfileRepository mappings;
    @Autowired IntegrationSourceRepository sources;
    @Autowired IntegrationSourceService service;
    @Autowired ObjectMapper json;

    @Test
    void readinessRequiresPublishedCoherentSchemaAndMappingBeforeActivation() {
        Fixture f=fixture("ready");
        var created=service.create(f.tenant.getId(), command(f));
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(service.validate(f.tenant.getId(),created.id()).ready()).isFalse();

        f.schema.validated(); f.schema.publish(); schemas.saveAndFlush(f.schema);
        f.mapping.validated(); f.mapping.publish(); mappings.saveAndFlush(f.mapping);

        var readiness=service.validate(f.tenant.getId(),created.id());
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.checks()).allMatch(c->c.state()==IntegrationSourceService.CheckState.READY);

        var active=service.activate(f.tenant.getId(),created.id(),created.version());
        assertThat(active.status()).isEqualTo("ACTIVE");
        var suspended=service.suspend(f.tenant.getId(),created.id(),active.version());
        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        var resumed=service.activate(f.tenant.getId(),created.id(),suspended.version());
        assertThat(resumed.status()).isEqualTo("ACTIVE");
        var archived=service.archive(f.tenant.getId(),created.id(),resumed.version());
        assertThat(archived.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void rejectsCrossTenantReferencesDuplicateCodesAndStaleVersions() {
        Fixture a=fixture("a"); Fixture b=fixture("b");
        assertThatThrownBy(()->service.create(a.tenant.getId(),new IntegrationSourceService.CreateCommand(
                "cross-source","Cross",b.client.getId(),a.schemaDefinition.getId(),a.mappingDefinition.getId(),
                "STANDARD",json.createObjectNode(),json.createObjectNode(),json.createObjectNode())))
                .isInstanceOf(IllegalArgumentException.class);

        var created=service.create(a.tenant.getId(),command(a));
        assertThatThrownBy(()->service.create(a.tenant.getId(),command(a)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.update(a.tenant.getId(),created.id(),created.version()+1,
                new IntegrationSourceService.UpdateCommand("Changed",a.client.getId(),a.schemaDefinition.getId(),
                        a.mappingDefinition.getId(),"STANDARD",json.createObjectNode(),json.createObjectNode(),json.createObjectNode())))
                .isInstanceOf(io.collectra.api.integration.application.IntegrationSourceConflictException.class).hasMessageContaining("version conflict");
        assertThatThrownBy(()->service.get(b.tenant.getId(),created.id()))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void rejectsActivationWhenPublishedMappingTargetsAnotherSchemaVersion() {
        Fixture f=fixture("mismatch");
        SourceSchema another=schemas.saveAndFlush(new SourceSchema(f.tenant.getId(),f.schemaDefinition.getId(),
                f.schema.getCode(),"Other version",SourceFormat.JSON,2));
        another.validated(); another.publish(); schemas.saveAndFlush(another);
        f.mapping.validated(); f.mapping.publish(); mappings.saveAndFlush(f.mapping);
        var created=service.create(f.tenant.getId(),command(f));
        var readiness=service.validate(f.tenant.getId(),created.id());
        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.checks()).anyMatch(c->c.code().equals("MAPPING_SCHEMA_COHERENT")
                && c.state()==IntegrationSourceService.CheckState.BLOCKED);
        assertThatThrownBy(()->service.activate(f.tenant.getId(),created.id(),created.version()))
                .isInstanceOf(io.collectra.api.integration.application.IntegrationSourceConflictException.class).hasMessageContaining("not ready");
    }

    private IntegrationSourceService.CreateCommand command(Fixture f){
        return new IntegrationSourceService.CreateCommand("source-"+f.suffix,"Source "+f.suffix,f.client.getId(),
                f.schemaDefinition.getId(),f.mappingDefinition.getId(),"STANDARD",
                json.createObjectNode(),json.createObjectNode(),json.createObjectNode());
    }

    private Fixture fixture(String suffix){
        Tenant tenant=tenants.saveAndFlush(new Tenant("is-"+suffix+"-"+UUID.randomUUID(),"Tenant"));
        var client=clients.saveAndFlush(new io.collectra.api.integration.domain.ServiceClient(
                tenant.getId(),"client-"+suffix+"-"+UUID.randomUUID(),"Client",Set.of("integration:imports:create"),null));
        var sd=schemaDefinitions.saveAndFlush(new SourceSchemaDefinition(tenant.getId(),"SCHEMA_"+suffix.toUpperCase(),"Schema"));
        var schema=schemas.saveAndFlush(new SourceSchema(tenant.getId(),sd.getId(),"SCHEMA_"+suffix.toUpperCase(),"Schema",SourceFormat.JSON,1));
        var md=mappingDefinitions.saveAndFlush(new MappingProfileDefinition(tenant.getId(),"MAP_"+suffix.toUpperCase(),"Mapping","INVOICE"));
        var mapping=mappings.saveAndFlush(new MappingProfile(tenant.getId(),md.getId(),schema.getId(),"MAP_"+suffix.toUpperCase(),"Mapping","INVOICE",1));
        return new Fixture(suffix,tenant,client,sd,schema,md,mapping);
    }

    private record Fixture(String suffix,Tenant tenant,io.collectra.api.integration.domain.ServiceClient client,
            SourceSchemaDefinition schemaDefinition,SourceSchema schema,MappingProfileDefinition mappingDefinition,MappingProfile mapping){}
}
