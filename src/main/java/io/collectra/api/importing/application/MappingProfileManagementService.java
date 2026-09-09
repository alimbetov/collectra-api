package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.domain.MappingProfile;
import io.collectra.api.importing.domain.MappingProfileDefinition;
import io.collectra.api.importing.domain.MappingRule;
import io.collectra.api.importing.infrastructure.MappingProfileDefinitionRepository;
import io.collectra.api.importing.infrastructure.MappingProfileRepository;
import io.collectra.api.importing.infrastructure.MappingRuleRepository;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import io.collectra.api.template.infrastructure.FieldDefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MappingProfileManagementService {
    private static final Set<String> SUPPORTED = Set.of(
            "NONE", "TRIM", "UPPERCASE", "LOWERCASE", "DATE_PARSE", "DECIMAL_PARSE",
            "BOOLEAN_PARSE", "SPLIT", "CHANNELS_PARSE", "NORMALIZE_PHONE", "VALIDATE_EMAIL");
    private final MappingProfileDefinitionRepository definitions;
    private final MappingProfileRepository versions;
    private final MappingRuleRepository rules;
    private final SourceSchemaRepository schemas;
    private final SourceFieldRepository sourceFields;
    private final FieldDefinitionRepository targetFields;
    private final MappingExecutionService execution;
    private final ObjectMapper json;

    public MappingProfileManagementService(MappingProfileDefinitionRepository definitions,
            MappingProfileRepository versions, MappingRuleRepository rules,
            SourceSchemaRepository schemas, SourceFieldRepository sourceFields,
            FieldDefinitionRepository targetFields, MappingExecutionService execution,
            ObjectMapper json) {
        this.definitions = definitions;
        this.versions = versions;
        this.rules = rules;
        this.schemas = schemas;
        this.sourceFields = sourceFields;
        this.targetFields = targetFields;
        this.execution = execution;
        this.json = json;
    }

    @Transactional
    public MappingProfileDefinition create(UUID tenantId, String code, String name,
            String documentType) {
        if (definitions.existsByTenantIdAndCodeIgnoreCase(tenantId, code))
            throw new IllegalArgumentException("Mapping profile code already exists");
        return definitions.save(new MappingProfileDefinition(tenantId, normalizeCode(code),
                name.trim(), normalizeCode(documentType)));
    }

    @Transactional(readOnly = true)
    public List<MappingProfileDefinition> list(UUID tenantId) {
        return definitions.findAllByTenantIdOrderByCodeAsc(tenantId);
    }

    @Transactional
    public MappingProfile createVersion(UUID tenantId, UUID definitionId, UUID sourceSchemaVersionId) {
        var definition = requireDefinition(tenantId, definitionId);
        var schema = schemas.findByIdAndTenantId(sourceSchemaVersionId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Source schema version not found"));
        if (schema.getStatus() != DefinitionStatus.PUBLISHED)
            throw new IllegalArgumentException("Mapping must reference a published source schema version");
        var latest = versions.findTopByDefinitionIdOrderByProfileVersionDesc(definitionId);
        int number = latest.map(v -> v.getProfileVersion() + 1).orElse(1);
        var created = versions.save(new MappingProfile(tenantId, definitionId, schema.getId(),
                definition.getCode(), definition.getName(), definition.getDocumentType(), number));
        latest.ifPresent(previous -> rules.findAllByMappingProfileId(previous.getId())
                .forEach(rule -> rules.save(new MappingRule(created.getId(), rule.getSourceFieldId(),
                        rule.getTargetFieldId(), rule.getTransformation().deepCopy(),
                        rule.getDefaultValue(), rule.isRequired()))));
        return created;
    }

    @Transactional(readOnly = true)
    public List<MappingProfile> versions(UUID tenantId, UUID definitionId) {
        requireDefinition(tenantId, definitionId);
        return versions.findAllByDefinitionIdOrderByProfileVersionDesc(definitionId);
    }

    @Transactional
    public MappingRule addRule(UUID tenantId, UUID versionId, UUID sourceFieldId,
            UUID targetFieldId, JsonNode transformation, String defaultValue, boolean required) {
        var version = requireDraft(tenantId, versionId);
        validateRuleReferences(tenantId, version, sourceFieldId, targetFieldId, transformation);
        return rules.save(new MappingRule(versionId, sourceFieldId, targetFieldId,
                normalizedTransformation(transformation), defaultValue, required));
    }

    @Transactional
    public MappingRule updateRule(UUID tenantId, UUID versionId, UUID ruleId, UUID sourceFieldId,
            UUID targetFieldId, JsonNode transformation, String defaultValue, boolean required) {
        var version = requireDraft(tenantId, versionId);
        validateRuleReferences(tenantId, version, sourceFieldId, targetFieldId, transformation);
        var rule = rules.findByIdAndMappingProfileId(ruleId, versionId)
                .orElseThrow(() -> new NoSuchElementException("Mapping rule not found"));
        rule.update(sourceFieldId, targetFieldId, normalizedTransformation(transformation),
                defaultValue, required);
        return rule;
    }

    @Transactional
    public void deleteRule(UUID tenantId, UUID versionId, UUID ruleId) {
        requireDraft(tenantId, versionId);
        rules.delete(rules.findByIdAndMappingProfileId(ruleId, versionId)
                .orElseThrow(() -> new NoSuchElementException("Mapping rule not found")));
    }

    @Transactional(readOnly = true)
    public List<MappingRule> rules(UUID tenantId, UUID versionId) {
        requireVersion(tenantId, versionId);
        return rules.findAllByMappingProfileId(versionId);
    }

    @Transactional
    public SourceSchemaManagementService.ValidationResult validate(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        if (version.getStatus() != DefinitionStatus.DRAFT)
            throw new IllegalArgumentException("Only draft mapping profile can be validated");
        List<SourceSchemaManagementService.ValidationIssue> errors = new ArrayList<>();
        var schema = schemas.findByIdAndTenantId(version.getSourceSchemaId(), tenantId).orElse(null);
        if (schema == null || schema.getStatus() != DefinitionStatus.PUBLISHED)
            errors.add(issue("SOURCE_SCHEMA_NOT_PUBLISHED", "sourceSchemaVersionId",
                    "A published source schema version is required"));
        var configured = rules.findAllByMappingProfileId(versionId);
        if (configured.isEmpty()) errors.add(issue("RULES_EMPTY", "rules",
                "At least one mapping rule is required"));
        java.util.Set<UUID> targets = new java.util.HashSet<>();
        java.util.Set<UUID> sources = new java.util.HashSet<>();
        for (var rule : configured) {
            sources.add(rule.getSourceFieldId());
            if (!targets.add(rule.getTargetFieldId()))
                errors.add(issue("DUPLICATE_TARGET", "rules", "Target field is mapped more than once"));
            try {
                validateRuleReferences(tenantId, version, rule.getSourceFieldId(),
                        rule.getTargetFieldId(), rule.getTransformation());
            } catch (IllegalArgumentException | NoSuchElementException ex) {
                errors.add(issue("INVALID_RULE", "rules." + rule.getId(), ex.getMessage()));
            }
        }
        if (schema != null) {
            sourceFields.findAllBySourceSchemaIdOrderByPositionAsc(schema.getId()).stream()
                    .filter(io.collectra.api.importing.domain.SourceField::isRequired)
                    .filter(source -> !sources.contains(source.getId()))
                    .forEach(source -> errors.add(issue("REQUIRED_SOURCE_NOT_MAPPED", "rules",
                            "Required source field is not mapped: " + source.getSourcePath())));
        }
        targetFields.findAvailable(tenantId).stream()
                .filter(io.collectra.api.template.domain.FieldDefinition::isRequired)
                .filter(field -> field.getCategory().equalsIgnoreCase(version.getDocumentType()))
                .filter(field -> !targets.contains(field.getId()))
                .forEach(field -> errors.add(issue("REQUIRED_TARGET_NOT_MAPPED", "rules",
                        "Required target field is not mapped: " + field.getKey())));
        if (errors.isEmpty()) version.validated();
        return new SourceSchemaManagementService.ValidationResult(errors.isEmpty(), errors, List.of());
    }

    @Transactional(readOnly = true)
    public MappingExecutionService.MappingResult test(UUID tenantId, UUID versionId, byte[] input) {
        requireVersion(tenantId, versionId);
        return execution.test(tenantId, versionId, input);
    }

    @Transactional(readOnly = true)
    public MappingExecutionService.RuleTestResult testRule(UUID tenantId, UUID versionId,
            UUID ruleId, JsonNode sourceValue) {
        requireVersion(tenantId, versionId);
        return execution.testRule(tenantId, versionId, ruleId, sourceValue);
    }

    @Transactional
    public MappingProfile publish(UUID tenantId, UUID id) { var v = requireVersion(tenantId, id); v.publish(); return v; }
    @Transactional
    public MappingProfile reopen(UUID tenantId, UUID id) { var v = requireVersion(tenantId, id); v.reopen(); return v; }
    @Transactional
    public MappingProfile archive(UUID tenantId, UUID id) { var v = requireVersion(tenantId, id); v.archive(); return v; }

    private void validateRuleReferences(UUID tenantId, MappingProfile version, UUID sourceId,
            UUID targetId, JsonNode transformation) {
        var source = sourceFields.findByIdAndSourceSchemaId(sourceId, version.getSourceSchemaId())
                .orElseThrow(() -> new IllegalArgumentException("Source field does not belong to schema version"));
        var target = targetFields.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException("Target field not found"));
        if (!target.isSystem() && !tenantId.equals(target.getTenantId()))
            throw new IllegalArgumentException("Target field belongs to another tenant");
        if (!"ACTIVE".equals(target.getStatus()))
            throw new IllegalArgumentException("Target field is archived");
        validateTransformation(transformation);
    }

    private void validateTransformation(JsonNode transformation) {
        String type = transformation == null ? "NONE"
                : transformation.path("type").asText("NONE").toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(type)) throw new IllegalArgumentException("Unsupported transformation: " + type);
        if ("DATE_PARSE".equals(type) && transformation.path("pattern").asText().length() > 50)
            throw new IllegalArgumentException("Date pattern is too long");
    }

    private JsonNode normalizedTransformation(JsonNode value) {
        return value == null || value.isNull() ? json.createObjectNode().put("type", "NONE") : value;
    }

    private MappingProfileDefinition requireDefinition(UUID tenantId, UUID id) {
        return definitions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Mapping profile not found"));
    }
    private MappingProfile requireVersion(UUID tenantId, UUID id) {
        return versions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Mapping profile version not found"));
    }
    private MappingProfile requireDraft(UUID tenantId, UUID id) {
        var v = requireVersion(tenantId, id);
        if (v.getStatus() != DefinitionStatus.DRAFT)
            throw new IllegalArgumentException("Only draft mapping profile can be changed");
        return v;
    }
    private String normalizeCode(String code) {
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{1,99}")) throw new IllegalArgumentException("Invalid code");
        return value;
    }
    private SourceSchemaManagementService.ValidationIssue issue(String code, String path, String message) {
        return new SourceSchemaManagementService.ValidationIssue(code, path, message);
    }
}
