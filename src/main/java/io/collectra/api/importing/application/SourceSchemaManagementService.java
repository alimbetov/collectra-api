package io.collectra.api.importing.application;

import io.collectra.api.importing.domain.DefinitionStatus;
import io.collectra.api.importing.domain.SourceField;
import io.collectra.api.importing.domain.SourceFormat;
import io.collectra.api.importing.domain.SourceSchema;
import io.collectra.api.importing.domain.SourceSchemaDefinition;
import io.collectra.api.importing.infrastructure.SourceFieldRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaDefinitionRepository;
import io.collectra.api.importing.infrastructure.SourceSchemaRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceSchemaManagementService {
    private final SourceSchemaDefinitionRepository definitions;
    private final SourceSchemaRepository versions;
    private final SourceFieldRepository fields;

    public SourceSchemaManagementService(SourceSchemaDefinitionRepository definitions,
            SourceSchemaRepository versions, SourceFieldRepository fields) {
        this.definitions = definitions;
        this.versions = versions;
        this.fields = fields;
    }

    @Transactional
    public SourceSchemaDefinition create(UUID tenantId, String code, String name) {
        if (definitions.existsByTenantIdAndCodeIgnoreCase(tenantId, code))
            throw new IllegalArgumentException("Source schema code already exists");
        return definitions.save(new SourceSchemaDefinition(tenantId, normalizeCode(code), name.trim()));
    }

    @Transactional(readOnly = true)
    public List<SourceSchemaDefinition> list(UUID tenantId) {
        return definitions.findAllByTenantIdOrderByCodeAsc(tenantId);
    }

    @Transactional
    public SourceSchemaDefinition rename(UUID tenantId, UUID definitionId, String name) {
        var definition = requireDefinition(tenantId, definitionId);
        definition.rename(name.trim());
        return definition;
    }

    @Transactional
    public SourceSchema createVersion(UUID tenantId, UUID definitionId, SourceFormat format) {
        var definition = requireDefinition(tenantId, definitionId);
        var latest = versions.findTopByDefinitionIdOrderBySchemaVersionDesc(definitionId);
        int number = latest.map(v -> v.getSchemaVersion() + 1).orElse(1);
        var created = versions.save(new SourceSchema(tenantId, definitionId, definition.getCode(),
                definition.getName(), format, number));
        latest.ifPresent(previous -> fields.findAllBySourceSchemaIdOrderByPositionAsc(previous.getId())
                .forEach(field -> fields.save(new SourceField(created.getId(), field.getSourcePath(),
                        field.getDetectedType(), field.getSampleValue(), field.isRequired(),
                        field.getPosition()))));
        return created;
    }

    @Transactional(readOnly = true)
    public List<SourceSchema> versions(UUID tenantId, UUID definitionId) {
        requireDefinition(tenantId, definitionId);
        return versions.findAllByDefinitionIdOrderBySchemaVersionDesc(definitionId);
    }

    @Transactional
    public SourceField addField(UUID tenantId, UUID versionId, String path, String type,
            String sample, boolean required, Integer position) {
        var version = requireDraft(tenantId, versionId);
        return fields.save(new SourceField(version.getId(), path.trim(), type, sample, required, position));
    }

    @Transactional
    public SourceField updateField(UUID tenantId, UUID versionId, UUID fieldId, String path,
            String type, String sample, boolean required, Integer position) {
        requireDraft(tenantId, versionId);
        var field = fields.findByIdAndSourceSchemaId(fieldId, versionId)
                .orElseThrow(() -> new NoSuchElementException("Source field not found"));
        field.update(path.trim(), type, sample, required, position);
        return field;
    }

    @Transactional
    public void deleteField(UUID tenantId, UUID versionId, UUID fieldId) {
        requireDraft(tenantId, versionId);
        var field = fields.findByIdAndSourceSchemaId(fieldId, versionId)
                .orElseThrow(() -> new NoSuchElementException("Source field not found"));
        fields.delete(field);
    }

    @Transactional(readOnly = true)
    public List<SourceField> fields(UUID tenantId, UUID versionId) {
        requireVersion(tenantId, versionId);
        return fields.findAllBySourceSchemaIdOrderByPositionAsc(versionId);
    }

    @Transactional
    public ValidationResult validate(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        if (version.getStatus() != DefinitionStatus.DRAFT)
            throw new IllegalArgumentException("Only draft schema can be validated");
        var errors = new java.util.ArrayList<ValidationIssue>();
        var configured = fields.findAllBySourceSchemaIdOrderByPositionAsc(versionId);
        if (configured.isEmpty()) errors.add(new ValidationIssue("FIELDS_EMPTY", "fields",
                "At least one source field is required"));
        if (errors.isEmpty()) version.validated();
        return new ValidationResult(errors.isEmpty(), errors, List.of());
    }

    @Transactional
    public SourceSchema publish(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        version.publish();
        return version;
    }

    @Transactional
    public SourceSchema reopen(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        version.reopen();
        return version;
    }

    @Transactional
    public SourceSchema archive(UUID tenantId, UUID versionId) {
        var version = requireVersion(tenantId, versionId);
        version.archive();
        return version;
    }

    private SourceSchemaDefinition requireDefinition(UUID tenantId, UUID id) {
        return definitions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Source schema not found"));
    }

    private SourceSchema requireVersion(UUID tenantId, UUID id) {
        return versions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Source schema version not found"));
    }

    private SourceSchema requireDraft(UUID tenantId, UUID id) {
        var version = requireVersion(tenantId, id);
        if (version.getStatus() != DefinitionStatus.DRAFT)
            throw new IllegalArgumentException("Only draft schema can be changed");
        return version;
    }

    private String normalizeCode(String code) {
        String value = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{1,99}"))
            throw new IllegalArgumentException("Invalid source schema code");
        return value;
    }

    public record ValidationIssue(String code, String path, String message) {}
    public record ValidationResult(boolean valid, List<ValidationIssue> errors,
            List<ValidationIssue> warnings) {}
}
