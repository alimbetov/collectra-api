package io.collectra.api.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.infrastructure.MappingProfileDefinitionRepository;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaDefinitionRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.integration.domain.IntegrationSource;
import io.collectra.api.integration.infrastructure.IntegrationSourceRepository;
import io.collectra.api.integration.infrastructure.ServiceClientRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationSourceService {
    private final IntegrationSourceRepository sources;
    private final ServiceClientRepository clients;
    private final SourceSchemaDefinitionRepository schemaDefinitions;
    private final SourceSchemaRepository schemas;
    private final MappingProfileDefinitionRepository mappingDefinitions;
    private final MappingProfileRepository mappings;
    private final ObjectMapper json;

    public IntegrationSourceService(
            IntegrationSourceRepository sources,
            ServiceClientRepository clients,
            SourceSchemaDefinitionRepository schemaDefinitions,
            SourceSchemaRepository schemas,
            MappingProfileDefinitionRepository mappingDefinitions,
            MappingProfileRepository mappings,
            ObjectMapper json) {
        this.sources = sources;
        this.clients = clients;
        this.schemaDefinitions = schemaDefinitions;
        this.schemas = schemas;
        this.mappingDefinitions = mappingDefinitions;
        this.mappings = mappings;
        this.json = json;
    }

    @Transactional
    public SourceResponse create(UUID tenantId, CreateCommand c) {
        String code = normalizeCode(c.code());
        if (sources.existsByTenantIdAndCode(tenantId, code))
            throw new IntegrationSourceConflictException(
                    "INTEGRATION_SOURCE_CODE_EXISTS", "Integration source code already exists");
        validateReferences(
                tenantId,
                c.serviceClientId(),
                c.sourceSchemaDefinitionId(),
                c.mappingProfileDefinitionId());
        IntegrationSource value =
                sources.save(
                        new IntegrationSource(
                                tenantId,
                                code,
                                c.name().trim(),
                                c.serviceClientId(),
                                c.sourceSchemaDefinitionId(),
                                c.mappingProfileDefinitionId(),
                                normalizeMode(c.processingMode()),
                                canonicalJson(c.headerMapping()),
                                canonicalJson(c.resourcePolicy()),
                                canonicalJson(c.routingConfig())));
        return response(value);
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> list(UUID tenantId) {
        return sources.findAllByTenantIdOrderByCodeAsc(tenantId).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public SourceResponse get(UUID tenantId, UUID id) {
        return response(require(tenantId, id));
    }

    @Transactional
    public SourceResponse update(UUID tenantId, UUID id, long expectedVersion, UpdateCommand c) {
        IntegrationSource value = require(tenantId, id);
        checkVersion(value, expectedVersion);
        validateReferences(
                tenantId,
                c.serviceClientId(),
                c.sourceSchemaDefinitionId(),
                c.mappingProfileDefinitionId());
        value.update(
                c.name().trim(),
                c.serviceClientId(),
                c.sourceSchemaDefinitionId(),
                c.mappingProfileDefinitionId(),
                normalizeMode(c.processingMode()),
                canonicalJson(c.headerMapping()),
                canonicalJson(c.resourcePolicy()),
                canonicalJson(c.routingConfig()));
        sources.flush();
        return response(value);
    }

    @Transactional(readOnly = true)
    public ReadinessResponse validate(UUID tenantId, UUID id) {
        IntegrationSource s = require(tenantId, id);
        return readiness(tenantId, s);
    }

    @Transactional
    public SourceResponse activate(UUID tenantId, UUID id, long expectedVersion) {
        IntegrationSource s = require(tenantId, id);
        checkVersion(s, expectedVersion);
        ReadinessResponse r = readiness(tenantId, s);
        if (!r.ready())
            throw new IntegrationSourceConflictException(
                    "INTEGRATION_SOURCE_NOT_READY",
                    "Integration source is not ready: "
                            + r.checks().stream()
                                    .filter(x -> x.state() == CheckState.BLOCKED)
                                    .map(ReadinessCheck::code)
                                    .toList());
        s.activate();
        sources.flush();
        return response(s);
    }

    @Transactional
    public SourceResponse suspend(UUID tenantId, UUID id, long expectedVersion) {
        IntegrationSource s = require(tenantId, id);
        checkVersion(s, expectedVersion);
        s.suspend();
        sources.flush();
        return response(s);
    }

    @Transactional
    public SourceResponse archive(UUID tenantId, UUID id, long expectedVersion) {
        IntegrationSource s = require(tenantId, id);
        checkVersion(s, expectedVersion);
        s.archive();
        sources.flush();
        return response(s);
    }

    private ReadinessResponse readiness(UUID tenantId, IntegrationSource s) {
        var client = clients.findByIdAndTenantId(s.getServiceClientId(), tenantId);
        boolean clientReady = client.map(x -> x.activeAt(java.time.Instant.now())).orElse(false);
        var publishedSchema =
                schemas
                        .findAllByDefinitionIdOrderBySchemaVersionDesc(
                                s.getSourceSchemaDefinitionId())
                        .stream()
                        .filter(
                                x ->
                                        x.getTenantId().equals(tenantId)
                                                && x.getStatus() == DefinitionStatus.PUBLISHED)
                        .findFirst();
        var publishedMapping =
                mappings
                        .findAllByDefinitionIdOrderByProfileVersionDesc(
                                s.getMappingProfileDefinitionId())
                        .stream()
                        .filter(
                                x ->
                                        x.getTenantId().equals(tenantId)
                                                && x.getStatus() == DefinitionStatus.PUBLISHED)
                        .findFirst();
        boolean mappingCoherent =
                publishedMapping.isPresent()
                        && publishedSchema.isPresent()
                        && publishedMapping
                                .get()
                                .getSourceSchemaId()
                                .equals(publishedSchema.get().getId());
        List<ReadinessCheck> checks =
                List.of(
                        check("SERVICE_CLIENT", clientReady, "SERVICE_CLIENT"),
                        check(
                                "SOURCE_SCHEMA_PUBLISHED",
                                publishedSchema.isPresent(),
                                "SOURCE_SCHEMA"),
                        check(
                                "MAPPING_PROFILE_PUBLISHED",
                                publishedMapping.isPresent(),
                                "MAPPING_PROFILE"),
                        check("MAPPING_SCHEMA_COHERENT", mappingCoherent, "MAPPING_PROFILE"));
        return new ReadinessResponse(
                s.getId(),
                s.getCode(),
                s.getStatus().name(),
                checks.stream().allMatch(x -> x.state() == CheckState.READY),
                checks);
    }

    private ReadinessCheck check(String code, boolean ok, String resource) {
        return new ReadinessCheck(code, ok ? CheckState.READY : CheckState.BLOCKED, resource);
    }

    private void validateReferences(UUID tenantId, UUID client, UUID schema, UUID mapping) {
        clients.findByIdAndTenantId(client, tenantId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Service client does not belong to tenant"));
        schemaDefinitions
                .findByIdAndTenantId(schema, tenantId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Source schema definition does not belong to tenant"));
        mappingDefinitions
                .findByIdAndTenantId(mapping, tenantId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Mapping profile definition does not belong to tenant"));
    }

    private IntegrationSource require(UUID tenantId, UUID id) {
        return sources.findByIdAndTenantId(id, tenantId).orElseThrow();
    }

    private void checkVersion(IntegrationSource s, long expected) {
        if (s.getVersion() != expected)
            throw new IntegrationSourceConflictException(
                    "VERSION_CONFLICT", "Integration source version conflict");
    }

    private String normalizeCode(String v) {
        if (v == null || !v.matches("[a-z0-9-]{3,100}"))
            throw new IllegalArgumentException("Invalid integration source code");
        return v;
    }

    private String normalizeMode(String v) {
        String n = v == null ? "STANDARD" : v.trim().toUpperCase();
        if (!n.equals("STANDARD"))
            throw new IllegalArgumentException("Unsupported processing mode");
        return n;
    }

    private String canonicalJson(JsonNode value) {
        try {
            return json.writeValueAsString(value == null ? json.createObjectNode() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON configuration", e);
        }
    }

    private SourceResponse response(IntegrationSource s) {
        try {
            return new SourceResponse(
                    s.getId(),
                    s.getCode(),
                    s.getName(),
                    s.getStatus().name(),
                    s.getServiceClientId(),
                    s.getSourceSchemaDefinitionId(),
                    s.getMappingProfileDefinitionId(),
                    s.getProcessingMode(),
                    json.readTree(s.getHeaderMapping()),
                    json.readTree(s.getResourcePolicy()),
                    json.readTree(s.getRoutingConfig()),
                    s.getVersion());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record CreateCommand(
            String code,
            String name,
            UUID serviceClientId,
            UUID sourceSchemaDefinitionId,
            UUID mappingProfileDefinitionId,
            String processingMode,
            JsonNode headerMapping,
            JsonNode resourcePolicy,
            JsonNode routingConfig) {}

    public record UpdateCommand(
            String name,
            UUID serviceClientId,
            UUID sourceSchemaDefinitionId,
            UUID mappingProfileDefinitionId,
            String processingMode,
            JsonNode headerMapping,
            JsonNode resourcePolicy,
            JsonNode routingConfig) {}

    public record SourceResponse(
            UUID id,
            String code,
            String name,
            String status,
            UUID serviceClientId,
            UUID sourceSchemaDefinitionId,
            UUID mappingProfileDefinitionId,
            String processingMode,
            JsonNode headerMapping,
            JsonNode resourcePolicy,
            JsonNode routingConfig,
            long version) {}

    public record ReadinessResponse(
            UUID sourceId,
            String sourceCode,
            String status,
            boolean ready,
            List<ReadinessCheck> checks) {}

    public record ReadinessCheck(String code, CheckState state, String resourceType) {}

    public enum CheckState {
        READY,
        BLOCKED,
        NOT_APPLICABLE
    }
}
