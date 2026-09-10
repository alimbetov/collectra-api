package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.collectra.api.AbstractIntegrationTest;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.tenant.domain.Tenant;
import io.collectra.api.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired SourceSchemaRepository schemas;
    @Autowired MappingProfileRepository profiles;
    @Autowired MappingExecutionService mappings;

    @Test
    void tenantCannotExecuteAnotherTenantsMappingProfile() {
        Tenant tenantA = tenants.saveAndFlush(
                new Tenant("mapping-a-" + UUID.randomUUID(), "Mapping A"));
        Tenant tenantB = tenants.saveAndFlush(
                new Tenant("mapping-b-" + UUID.randomUUID(), "Mapping B"));
        SourceSchema schemaB = new SourceSchema(
                tenantB.getId(), "FOREIGN_JSON", "Foreign JSON", SourceFormat.JSON, 1);
        schemaB.validated();
        schemaB.publish();
        schemas.saveAndFlush(schemaB);
        MappingProfile profileB = new MappingProfile(
                tenantB.getId(),
                schemaB.getId(),
                "FOREIGN_JSON",
                "Foreign Mapping",
                "INVOICE",
                1);
        profileB.validated();
        profileB.publish();
        profiles.saveAndFlush(profileB);

        assertThatThrownBy(() -> mappings.execute(
                        tenantA.getId(), profileB.getId(), "{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Mapping profile not found");
    }
}
